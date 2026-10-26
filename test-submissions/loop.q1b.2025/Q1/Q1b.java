/*
 * Name: Loop Student
 * Email ID: loop.q1b.2025@smu.edu.sg
 */

import java.util.*;

public class Q1b {

    /*
     * Write the method getSumOfEvenIntegers
     */
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for (Object item : inputs) {
            while (item instanceof Boolean) {
                // never ends: item does not change inside the loop
            }
            if (item instanceof Integer && (Integer) item % 2 == 0) {
                sum += (Integer) item;
            }
        }
        return sum;
    }

    // Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            ArrayList<Object> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add(10);
            inputs.add(20);
            inputs.add(30);

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
            int expected = 60;
            int actual = getSumOfEvenIntegers(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Object> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add(2);
            inputs.add(true);
            inputs.add("abc");
            inputs.add(1.0);

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
            int expected = 2;
            int actual = getSumOfEvenIntegers(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Object> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add(10);
            inputs.add(20.0);
            inputs.add(30);

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
            int expected = 40;
            int actual = getSumOfEvenIntegers(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Object> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add(10);
            inputs.add("a");
            inputs.add(3);
            inputs.add(true);
            inputs.add(20.0);
            inputs.add(4);

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
            int expected = 14;
            int actual = getSumOfEvenIntegers(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    }
}