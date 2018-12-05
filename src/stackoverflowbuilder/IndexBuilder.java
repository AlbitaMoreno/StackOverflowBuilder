package stackoverflowbuilder;

import com.opencsv.CSVReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public final class IndexBuilder {
    private PerFieldAnalyzerWrapper ana;
    public Analyzer RcodeAnalyzer;
    private Similarity similarity;
    private static final String INDEX_DIRECTORY = "./../index"; 
    private static final String FACET_DIRECTORY = "./../facet"; 
    private IndexWriter writer;
    public Map<String, Analyzer> analyzerPerField = new HashMap<>();
    private List<String> palabras_codigo_r = Arrays.asList("for","if","else","function","while","case","break","do","try","catch","return",
    "objects","rm","assign","order","sort","numeric","character","integer");
    private FacetsConfig fconfig;
    private DirectoryTaxonomyWriter fwriter;
    private String[] meses;
    private Map<String, List<String>> etiquetas = new HashMap<>();
    
    public IndexBuilder(String ruta_preguntas, String ruta_respuestas, String ruta_etiquetas) throws IOException {
        this.meses = new String[]{"ENERO","FEBRERO","MARZO","ABRIL","MAYO","JUNIO",
            "JULIO","AGOSTO","SEPTIEMBRE","OCTUBRE","NOVIEMBRE","DICIEMBRE"};
        this.similarity = new BM25Similarity();
        
        // Creamos un analizador de código R 
        this.RcodeAnalyzer = new Analyzer(){
            @Override
            protected Analyzer.TokenStreamComponents createComponents(String string) {
                // Extraemos caracteres alfanuméricos
                final Tokenizer source = new LetterTokenizer();
                
                // Convertimos todo a minúscula
                TokenStream result = new LowerCaseFilter(source);
                
                // Elimino palabras vacías
                result = new StopFilter(result, new CharArraySet(palabras_codigo_r, false));
                
                return new TokenStreamComponents(source, result);
            }
        };
        
        // Creamos analizador por campo       
        analyzerPerField.put("Title", new StopAnalyzer());
        analyzerPerField.put("Body", new StopAnalyzer());
        analyzerPerField.put("Code", this.RcodeAnalyzer);

        this.ana = new PerFieldAnalyzerWrapper( new WhitespaceAnalyzer(), analyzerPerField);
        
        // Creamos el índice
        this.createIndex();
        System.out.print("Índice creado \n");
        
        // Añadimos los documentos
        this.indexDocuments(ruta_preguntas, ruta_respuestas, ruta_etiquetas);
        
        // Cerramos el índice
        this.close();
        System.out.print("Índice construido \n");
    }
    
    public void createIndex() throws IOException {
        // Abrimos directorio de índices
        FSDirectory dir = FSDirectory.open(Paths.get(INDEX_DIRECTORY));

        // Abrimos el directorio de facetas
        FSDirectory factDir = FSDirectory.open(Paths.get(FACET_DIRECTORY));
        
        // Configuramos índice al cual le vamos a pasar el StandardAnalyzer
        IndexWriterConfig config = new IndexWriterConfig(ana);
        config.setSimilarity(similarity); // Usamos como medida de similitud el Okapi BM25
        config.setOpenMode(OpenMode.CREATE); // Configuramos el índice de forma que se cree un índice si no 
                                             // está creado, si está creado simplemente se añaden los nuevos documentos
        
        // Configuramos facetas
        fconfig = new FacetsConfig(); 
        
        fconfig.setMultiValued("etiqueta", true); // Indicamos que la faceta etiqueta puede contener varios valores
        fconfig.setHierarchical("fecha", true); // Creamos una jerarquía para la faceta fecha
        
        // Construimos IndexWriter con los parámetros dados en config
        writer = new IndexWriter(dir, config); 
        
        // Construimos DirectoryTaxonomyWriter pásandole el directorio de facetas
        fwriter = new DirectoryTaxonomyWriter(factDir);
    }
    
    public void indexDocuments(String ruta_preguntas, String ruta_respuestas, String ruta_etiquetas) throws IOException {   
        // ETIQUETAS
        
        // Abro csv para extraer preguntas
        Reader reader = Files.newBufferedReader(Paths.get(ruta_etiquetas));
        CSVReader csvreader = new CSVReader(reader);
        
        csvreader.readNext(); // Evito hacer un documento con la columna de los nombres de los campos
        
        String [] d;
        while((d = csvreader.readNext()) != null) {
            if(etiquetas.containsKey(d[0])) {
                List<String> l = etiquetas.get(d[0]);
                l.add(d[1]);
                etiquetas.replace(d[0], etiquetas.get(d[0]), l);
            }
            else {
                List<String> l = new ArrayList<>();
                l.add(d[1]);
                etiquetas.put(d[0], l);
            }
        }
        
        csvreader.close(); // Cerramos csv
        
        // PREGUNTAS
        
        // Abro csv para extraer preguntas
        reader = Files.newBufferedReader(Paths.get(ruta_preguntas));
        csvreader = new CSVReader(reader);
        
        csvreader.readNext(); // Evito hacer un documento con la columna de los nombres de los campos

        int cont = 0;
        
        while((d = csvreader.readNext()) != null) {
            Document doc = new Document();
            
            System.out.println(d[0] + " " + d[1] + " " + cont + "\n");
            cont++; 
            
            // Incluimos los campos a indexar
            doc.add(new StringField("Id", d[0],Field.Store.YES));
            doc.add(new StringField("OwnerUserId", d[1],Field.Store.YES));
            doc.add(new StringField("CreationDate", d[2],Field.Store.YES));
            doc.add(new TextField("Title", d[4],Field.Store.YES));
            doc.add(new TextField("Body", d[5],Field.Store.YES));
            
            // Este campo lo almacenamos y lo creamos como campo de ordenación
            doc.add(new NumericDocValuesField("Score", Integer.parseInt(d[3])));
            doc.add(new StoredField("Score", Integer.parseInt(d[3])));
            
            org.jsoup.nodes.Document code = Jsoup.parse(d[5]);
            
            for (Element e : code.getAllElements()){
              if(e.tagName().equals("code")){
                  doc.add(new TextField("Code", e.text(),Field.Store.YES));
              }  
            }
            
            // Incluimos las facetas
            doc.add(new FacetField("es_pregunta", "true"));
            
            // Extraemos el mes y el año
            String [] fecha;
            fecha = d[2].split("-");
            doc.add(new FacetField("fecha", fecha[0], meses[Integer.parseInt(fecha[1]) - 1]));
            
            doc.add(new FacetField("puntuacion", this.categorizeScore(Integer.parseInt(d[3]))));
            
            List<String> et = etiquetas.get(d[0]);
            if(et != null) et.forEach((e) -> { doc.add(new FacetField("etiqueta", e)); });          
            
            writer.addDocument(fconfig.build(fwriter, doc));
        }
        
        csvreader.close(); // Cerramos csv
        
        System.out.print("Preguntas indexadas \n");
        
        // RESPUESTAS
        
        // Abro csv para extraer preguntas
        reader = Files.newBufferedReader(Paths.get(ruta_respuestas));
        csvreader = new CSVReader(reader);
        
        csvreader.readNext(); // Evito hacer un documento con la columna de los nombres de los campos
        
        cont = 0;
        
        while((d = csvreader.readNext()) != null) {  
            Document doc = new Document();
            
            System.out.println(d[0] + " " + d[1] + " " + d[3] + " " + cont + "\n");
            cont++; 
                      
            doc.add(new StringField("Id", d[0],Field.Store.YES));
            doc.add(new StringField("OwnerUserId", d[1],Field.Store.YES));
            doc.add(new StringField("CreationDate", d[2],Field.Store.YES));
            doc.add(new TextField("ParentId", d[3],Field.Store.NO));
            doc.add(new TextField("IsAcceptedAnswer", d[5],Field.Store.YES));            
            doc.add(new TextField("Body", d[6],Field.Store.YES));
            
            // Este campo lo almacenamos y lo creamos como campo de ordenación
            doc.add(new NumericDocValuesField("Score", Integer.parseInt(d[4])));
            doc.add(new StoredField("Score", Integer.parseInt(d[4])));
            
            org.jsoup.nodes.Document code = Jsoup.parse(d[5]);
            
            for (Element e : code.getAllElements()){
              if(e.tagName().equals("code")){
                    doc.add(new TextField("Code", e.text(),Field.Store.YES));
              }  
            }          
            
            // Incluimos las facetas
            doc.add(new FacetField("es_pregunta", "false"));
            doc.add(new FacetField("respuesta_aceptada", d[5]));
            
            // Extraemos el mes y el año
            String [] fecha;
            fecha = d[2].split("-");
            doc.add(new FacetField("fecha", fecha[0], meses[Integer.parseInt(fecha[1]) - 1]));
            
            doc.add(new FacetField("puntuacion", this.categorizeScore(Integer.parseInt(d[3]))));
            
            List<String> et = etiquetas.get(d[0]);
            if(et != null) et.forEach((e) -> { doc.add(new FacetField("etiqueta", e)); });   
            
            writer.addDocument(fconfig.build(fwriter, doc));
        }
        
        csvreader.close(); // Cerramos csv
        
        System.out.print("Respuestas indexadas \n");
    
    }

    public void close(){
        try{
            // Ejecutamos todos los cambios pendientes en el índice
            writer.commit();
            // Cerramos el IndexWriter
            writer.close();
            // Cerramos el DirectoryTaxonomyWriter
            fwriter.close();
        } catch (IOException e){
            System.out.println("Error closing index " + e);
        }
       
    }  
    
    private String categorizeScore(int score) {
        if(score < 50) return "Bajo";
        else if(score < 100) return "Medio";
        else return "Alto";
    }
}
