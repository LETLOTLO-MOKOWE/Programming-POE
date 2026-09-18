/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package chatapplication;

import java.util.Scanner;

/**
 *
 * @author Student
 */
public class ChatApplication {

    /**
     * @param args the command line arguments
     */
    
        // TODO code application logic here
       

/**
 * Console entry point for PROG5121 POE — Part 1.
 * Prompts the user through registration, then login, using Login.java's
 * validation and messaging logic. Registration is re-attempted until it
 * succeeds, since a user can't log in without a valid stored account.
 */


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Login login = new Login();

        System.out.println("=== User Registration ===");

        boolean registered = false;
        while (!registered) {
            System.out.print("Enter a username (must contain '_' and be no more than 5 characters): ");
            String username = scanner.nextLine();

            System.out.print("Enter a password (min 8 chars, 1 capital, 1 number, 1 special char): ");
            String password = scanner.nextLine();

            System.out.print("Enter your South African cell phone number (e.g. +27838968976): ");
            String cellPhoneNumber = scanner.nextLine();

            System.out.print("Enter your first name: ");
            String firstName = scanner.nextLine();

            System.out.print("Enter your last name: ");
            String lastName = scanner.nextLine();

            String result = login.registerUser(username, password, cellPhoneNumber, firstName, lastName);
            System.out.println(result);

            if (result.equals("Username successfully captured.")) {
                registered = true;
            } else {
                System.out.println("Please try registering again.\n");
            }
        }

        System.out.println("\n=== User Login ===");

        System.out.print("Enter your username: ");
        String loginUsername = scanner.nextLine();

        System.out.print("Enter your password: ");
        String loginPassword = scanner.nextLine();

        boolean loginSuccessful = login.loginUser(loginUsername, loginPassword);
        String statusMessage = login.returnLoginStatus(loginSuccessful);
        System.out.println(statusMessage);

        scanner.close();
    }
}
    
    

