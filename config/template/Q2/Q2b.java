/*
 * Name:
 * Email ID:
 */

import java.util.*;
import java.io.*;

public class Q2b {

     /*
     * Write the method getTopStudent
     */
    public static String getTopStudent(String filename, String courseName) {
        // insert your code here    
        return ""; // to make this code compile. Please modify accordingly!
    }

    //Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS101");
            String expected = "John LEE-4.0";
            String actual = getTopStudent("students.txt", "IS101");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS102");
            String expected = "Mary CHAN-3.5";
            String actual = getTopStudent("students.txt", "IS102");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            } System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS103");
            String expected = "CHAN Wei Jun-4.0";
            String actual = getTopStudent("students.txt", "IS103");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS104");
            try{
                String actual = getTopStudent("students.txt", "IS104");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "nosuchfile.txt", "IS101");
            try{
                String actual = getTopStudent("nosuchfile.txt", "IS101");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}