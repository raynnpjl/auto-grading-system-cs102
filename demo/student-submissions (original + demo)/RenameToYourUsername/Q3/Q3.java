/*
 * Name:
 * Email ID:
 */

import java.util.*;

public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        // insert your code here
        return new ArrayList<Shape>();
    }

    //Do not modify the following codes
    public static void main(String[] args) {
    
        int tcNum = 1;
        {
            ArrayList<Shape> shapes = new ArrayList<>();
            shapes.add(new Rectangle("R1", 1000, 2));
            shapes.add(new Rectangle("R2", 4, 2.5));
            shapes.add(new Circle("C1", 50));

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: ShapeComparator%n", tcNum++);
            String expected = "[R2=>10.0:13.0]";         
            List<Shape> actual = sortShapes(shapes);

            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual.toString())){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Shape> shapes = new ArrayList<>();
            shapes.add(new Rectangle("R1", 10, 2));
            shapes.add(new Rectangle("R2", 4, 2.5));
            shapes.add(new Rectangle("R3", 5, 6));

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: ShapeComparator%n", tcNum++);
            String expected = "[R3=>30.0:22.0, R1=>20.0:24.0, R2=>10.0:13.0]";         
            List<Shape> actual = sortShapes(shapes);

            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual.toString())){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Shape> shapes = new ArrayList<>();
            shapes.add(new Rectangle("R1", 8, 3));
            shapes.add(new Rectangle("R2", 6, 4));
            shapes.add(new Rectangle("R3", 12, 2));

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: ShapeComparator%n", tcNum++);
            String expected = "[R3=>24.0:28.0, R1=>24.0:22.0, R2=>24.0:20.0]";
            List<Shape> actual = sortShapes(shapes);

            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual.toString())){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<Shape> shapes = new ArrayList<>();
            shapes.add(new Rectangle("R1", 8, 3));
            shapes.add(new Rectangle("R2", 12, 2));
            shapes.add(new Circle("C1", 10));
            shapes.add(new Circle("C2", 1));
            
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: ShapeComparator%n", tcNum++);
            String expected = "[C1=>314.0:63.0, R2=>24.0:28.0, R1=>24.0:22.0, C2=>3.0:6.0]";
            List<Shape> actual = sortShapes(shapes);

            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual.toString())){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}