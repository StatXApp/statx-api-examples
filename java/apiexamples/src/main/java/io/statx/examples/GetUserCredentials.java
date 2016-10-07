package io.statx.examples;

import io.statx.rest.StatXClient;

/**
 * Sample code to show how to get the user credentials from StatX.
 *
 * Prerequisite: Download and install the StatX app
 *               from the AppStore (iOS) or PlayStore (Android).
 *
 * To build do:
 * mvn compile
 *
 * To run do:
 * mvn exec:java -Dexec.mainClass="io.statx.examples.GetUserCredentials" -Dexec.args="<myClientName> <+Phone Number>"
 *
 */
public class GetUserCredentials {
    public static void main(String[] args) throws Exception {
        System.out.println(args[0]);
        if (args.length < 2) {
            System.out.println("Usage java io.statx.examples.GetuserCredentials <ClientName> " +
                    "<PhoneNumber International Format>");
            System.exit(-1);
        }
        String clientName = args[0];
        String phoneNumber = args[1];

        // Lets sign up through the rest API and get an AuthToken. Once you get the credentials
        // you should save them somewhere safe for use at a later time.
        StatXClient statXClient = new StatXClient();
        StatXClient.UserCredential userCredential =
                statXClient.getCredentials(clientName, phoneNumber);
        System.out.println("Your API_KEY is: " + userCredential.getApiKey()
                + " Your AUTH_TOKEN is: " + userCredential.getAuthToken()
                + " Please keep them safe. ");
    }
}
