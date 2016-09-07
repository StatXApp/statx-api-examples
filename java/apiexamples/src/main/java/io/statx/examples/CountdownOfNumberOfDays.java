package io.statx.examples;

import io.statx.rest.ApiClient;
import io.statx.rest.ApiException;
import io.statx.rest.api.AuthenticationApi;
import io.statx.rest.api.GroupsApi;
import io.statx.rest.api.StatsApi;
import io.statx.rest.model.*;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Example to show how to update a number stat in StatX.
 * The class updates a number stat with the countdown of the number of days
 * from the current date to a given target date.
 *
 * Prerequisite: Download the app from the appstore (IOS) or playstore (android) and sign up.
 *
 * Build it with maven with:
 * mvn clean compile
 *
 * Call it with maven with:
 * mvn exec:java -Dexec.mainClass="io.statx.examples.CountdownOfNumberOfDays" -Dexec.args="<ClientId>
 *     <Phone Number in international format> <Target Date> <Stat Title>"
 *
 */
public class CountdownOfNumberOfDays {

    private static String STATX_REST_V1_URL_PREFIX = "https://api.statx.io/v1";

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage java io.statx.examples.CountdownOfNumberOfDays " +
                    "<clientName> <phoneNumber +<CountryCode><state><number>> " +
                    "<Target Date - YYYY/MM/DD> <Stat Title>");
            System.exit(-1);
        }
        String clientName = args[0];
        String phoneNumber = args[1];
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd");
        DateTime targetDate = dateTimeFormatter.parseDateTime(args[2]);
        String statTitle = args[3];

        // Lets sign up through the rest API and get an AuthToken. Once you get the credentials
        // you should save them somewhere safe for use at a later time.
        UserCredential userCredential = getCredentials(clientName, phoneNumber);
        ApiClient apiClient = getApiClient(userCredential);

        // Repeat once every 24 hours (see parameter below).
        while (true) {

            // Find the group with the countdown stat. If the group does not exist then create it.
            //
            // Note: The group name is not unique. In general it is not a good idea to use the group
            // name as a key to determine whether the group exists or not. If possible use the
            // groupid instead.
            String groupName = "ACountdownExample";
            GroupsApi groupsApi = new GroupsApi(apiClient);
            GroupList groupList = groupsApi.getGroups(groupName);
            Group group;
            if ((groupList == null) || (groupList.getData() == null) || (groupList.getData().isEmpty())) {
                // The group does not exist. Let's create one. Since we are creating the group
                // the api will add the current user as a member and admin of the group.
                group = new Group();
                group.setName(groupName);
                group = groupsApi.createGroup(group);
            } else {
                // Pick the first group (should be the only one).
                group = groupList.getData().get(0);
            }

            // Find the stat by name. If the stat does not exist then create it.
            //
            // Note: The stat title is not unique. In general it is not a good idea to use
            // the stat title as a key to determine whether the stat exists or not. If possible
            // use the statid instead.
            StatsApi statsApi = new StatsApi(apiClient);
            StatList statList = statsApi.getStats(group.getName(), statTitle);
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create a number stat.
                NumberStat number = new NumberStat();
                number.setTitle(statTitle);
                number.setVisualType(Stat.VisualTypeEnum.NUMBER);
                number.setGroupName(groupName);
                number.setValue(getRemainingDays(targetDate));
                statsApi.createStat(group.getId(), number);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Create the stat to update.
                NumberStat number = new NumberStat();
                number.setValue(getRemainingDays(targetDate));
                number.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, number);
            }

            System.out.println("Last update at: " + new Date(System.currentTimeMillis()));
            Thread.sleep(TimeUnit.DAYS.toMillis(1));
        }
    }

    private static String getRemainingDays(DateTime targetDate) {
        DateTime currentDate = new DateTime(System.currentTimeMillis());
        return Days.daysBetween(currentDate, targetDate).getDays() + "";
    }

    private static ApiClient getApiClient(UserCredential userCredential) {
        if (userCredential == null) {
            throw new RuntimeException("Unauthorized access. Please get user credentials first.");
        }
        ApiClient apiClient = new ApiClient();
        apiClient.addDefaultHeader("X-Auth-Token", userCredential.getAuthToken());
        apiClient.addDefaultHeader("X-API-KEY", userCredential.getApiKey());
        apiClient.setBasePath(STATX_REST_V1_URL_PREFIX);
        return apiClient;
    }

    private static ApiClient getApiClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(STATX_REST_V1_URL_PREFIX);
        return apiClient;
    }

    private static UserCredential getCredentials(String clientName, String phoneNumber) throws Exception {
        String clientId = requestVerificationCode(clientName, phoneNumber);

        // Go to the phone where you installed StatX. Select settings > Additional Authorizations.
        // Find the client with the name given above. Get the verification code an type it here.
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the verification code from your primary device");
        String verificationCode = Integer.toString(scanner.nextInt());
        VerificationCodeResponse response = login(clientId, phoneNumber, verificationCode);
        return new UserCredential(response.getApiKey(), response.getAuthToken());
    }

    private static String requestVerificationCode(String clientName, String phoneNumber) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setClientName(clientName);
        loginRequest.setPhoneNumber(phoneNumber);

        AuthenticationApi authenticationApi = new AuthenticationApi(getApiClient());
        LoginResponse loginResponse = authenticationApi.login(loginRequest);
        return loginResponse.getClientId();
    }

    private static VerificationCodeResponse login(String clientId, String phoneNumber, String verificationCode) throws ApiException {
        VerificationCodeRequest verificationCodeRequest = new VerificationCodeRequest();
        verificationCodeRequest.setClientId(clientId);
        verificationCodeRequest.setPhoneNumber(phoneNumber);
        verificationCodeRequest.setVerificationCode(verificationCode);
        AuthenticationApi authenticationApi = new AuthenticationApi(getApiClient());
        return authenticationApi.verifyCode(verificationCodeRequest);
    }

    // Small class to provide the user apiKey and authToken.
    private static class UserCredential {
        private final String apiKey;
        private final String authToken;

        UserCredential(String apiKey, String authToken) {
            this.apiKey = apiKey;
            this.authToken = authToken;
        }

        String getAuthToken() {
            return authToken;
        }

        String getApiKey() {
            return apiKey;
        }
    }

}
