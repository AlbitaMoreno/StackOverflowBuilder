/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package stackoverflowbuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Alba , Alberto
 */
public class StackOverflowBuilder {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // Índice
        IndexBuilder i = new IndexBuilder("./rquestions/Questions.csv", 
        "./rquestions/Answers.csv", "./rquestions/Tags.csv");
    }
    
}
