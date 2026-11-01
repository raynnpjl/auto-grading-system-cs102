/*
 * Name: Copied Answer
 * Email ID: copied.answer.2025@smu.edu.sg
 */

import java.util.*;
import java.io.*;

public class Q2a {

     /*
     * Write the method getAverageAge 
     */
    public static double getAverageAge(String filename, String surname) {
        // insert your code here
       try{
        File f = new File(filename);
        Scanner reader = new Scanner(f);
        double count = 0.0;
        double sum = 0.0;
        while(reader.hasNextLine()){
            String line = reader.nextLine();
            String[] parts = line.split("-");
            String name = parts[0];
            double age = Double.valueOf(parts[1]);
            String[] names = name.split(" ");
            if(names[0].toLowerCase().equals(surname.toLowerCase()) || names[names.length-1].toLowerCase().equals(surname.toLowerCase())){
                count++;
                sum += age;
            }
        }
        reader.close();
        if(count == 0){
            return 0.0;
        }
        return sum / count;
       }catch(FileNotFoundException e){
        return -1.0;
       }
        // to make this code compile. Please modify accordingly!
    }

    //Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "LEE");
            double expected = 36.0;
            double actual = getAverageAge("persons.txt", "LEE");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Lee");
            double expected = 36.0;
            double actual = getAverageAge("persons.txt", "Lee");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "WONG");
            double expected = 25.0;
            double actual = getAverageAge("persons.txt", "WONG");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Chan");
            double expected = 27.0;
            double actual = getAverageAge("persons.txt", "Chan");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Ong");
            double expected = 0.0;
            double actual = getAverageAge("persons.txt", "Ong");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

       {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "nosuchfile.txt", "Lee");
            double expected = -1.0;
            double actual = getAverageAge("nosuchfile.txt", "Lee");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}