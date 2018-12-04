package stackoverflowbuilder;

import java.io.IOException;

/**
 *
 * @author Alba , Alberto
 */
public class StackOverflowBuilder {

    public static void main(String[] args) throws IOException {
        // Índice
        IndexBuilder i = new IndexBuilder("./rquestions/Questions.csv", 
        "./rquestions/Answers.csv", "./rquestions/Tags.csv");
    }
    
}
