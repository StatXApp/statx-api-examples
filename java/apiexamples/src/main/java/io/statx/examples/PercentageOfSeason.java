/**
 * Copyright 2016 StatX Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package io.statx.examples;

import io.statx.rest.ApiClient;
import io.statx.rest.ApiException;
import io.statx.rest.api.AuthenticationApi;
import io.statx.rest.api.GroupsApi;
import io.statx.rest.api.StatsApi;
import io.statx.rest.model.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Example to show how to update a dialer stat in StatX.
 * Updates a dialer stat with the percentage days of the current season for the given date (UTC time).
 *
 * Prerequisite: Download the app from the appstore (IOS) or playstore (android) and sign up.
 *
 * Build it with maven with:
 * mvn clean compile
 *
 * Call it with maven with:
 * mvn exec:java -Dexec.mainClass="io.statx.examples.PercentageOfSeason" -Dexec.args="<ClientId>
 *     <Phone Number in international format> <Target Date> <Stat Title>"
 */
public class PercentageOfSeason {
    private static String STATX_REST_V1_URL_PREFIX = "https://api.statx.io/v1";
    private static String STAT_TITLE_PREFIX = "Percent of %s days";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage java io.statx.examples.PercentageOfSeason " +
                    "<clientName> <phoneNumber +<CountryCode><state><number>> <hemisphere - (N)orthern|(S)southern->");
            System.exit(-1);
        }
        String clientName = args[0];
        String phoneNumber = args[1];
        String hemisphereString = args[2];
        if (!hemisphereString.equals("N") && !hemisphereString.equals("S")) {
            System.out.println("Hemisphere should be either N - northern or S - southern");
            System.exit(-1);
        }

        // Lets sign up through the rest API and get an AuthToken. Once you get the credentials
        // you should save them somewhere safe for use at a later time.
        UserCredential userCredential = getCredentials(clientName, phoneNumber);
        ApiClient apiClient = getApiClient(userCredential);

        // Repeat once every 24 hours (see parameter below).
        while (true) {

            // Find the group with the dialer stat. If the group does not exist then create it.
            //
            // Note: The group name is not unique. In general it is not a good idea to use the group
            // name as a key to determine whether the group exists or not. If possible use the
            // groupid instead.
            String groupName = "StatX-API-Examples";
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
            Season season = Season.get(getCurrentUTCDateTime());
            if (season == null) {
                System.out.println("Invalid season");
                System.exit(-1);
            }
            Hemisphere hemisphere = Hemisphere.get(hemisphereString);
            if (hemisphere == null) {
                System.out.println("Invalid hemisphere value");
                System.exit(-1);
            }
            String statTitle = String.format(STAT_TITLE_PREFIX, season.getLabel(hemisphere));
            StatList statList = statsApi.getStats(group.getName(), statTitle);
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create a number stat.
                DialerStat dialerStat = new DialerStat();
                dialerStat.setTitle(statTitle);
                dialerStat.setVisualType(Stat.VisualTypeEnum.DIALER);
                dialerStat.setGroupName(groupName);
                dialerStat.setValue(season.getValue(getCurrentUTCDateTime()));
                statsApi.createStat(group.getId(), dialerStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Create the stat to update.
                DialerStat dialerStat = new DialerStat();
                DateTime dateTime = getCurrentUTCDateTime();
                dialerStat.setValue(season.getValue(dateTime));
                dialerStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, dialerStat);
            }

            System.out.println("Last update at: " + new Date(System.currentTimeMillis()));
            Thread.sleep(TimeUnit.DAYS.toMillis(1));
        }
    }

    private static DateTime getCurrentUTCDateTime() {
        return new DateTime(System.currentTimeMillis(), DateTimeZone.UTC);
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

    // Small enum for the hemisphere.
    private enum Hemisphere {
        NORTHERN("N"), SOUTHERN("S");

        private final String value;
        private static final Map<String, Hemisphere> map = new HashMap<>();

        static {
            map.put("N", NORTHERN);
            map.put("S", SOUTHERN);
        }

        Hemisphere(String value) {
            this.value = value;
        }

        static Hemisphere get(String value) {
            return map.get(value);
        }
    }

    // Enum for the Season.
    private enum Season {
        SPRING(80, 171, "spring", "fall"),
        SUMMER(172, 263, "summer", "winter"),
        FALL(264, 354, "fall", "spring"),
        // To deal with the year change we break winter
        // into two enum values.
        WINTER(355, 365, "winter", "summer"),
        WINTER2(1, 79, "winter", "summer");

        private final int startDay;
        private final int endDay;
        private final String nHemisphereLabel;
        private final String sHemisphereLabel;

        Season(int startDayOfYear, int endDayOfYear,
               String nHemisphereLabel, String sHemisphereLabel) {
            this.startDay = startDayOfYear;
            this.endDay = endDayOfYear;
            this.nHemisphereLabel = nHemisphereLabel;
            this.sHemisphereLabel = sHemisphereLabel;
        }

        /**
         * Returns the number of days since the beginning of the season to the given date.
         *
         * @param dateTime the given date
         * @return the number of days since the beginning of the season to the given date or -1 if
         * date is not within the season.
         */
        int daysInTheSeason(DateTime dateTime) {
            int startDay = this.startDay;
            int endDay = this.endDay;
            if (dateTime.year().isLeap()) {
                startDay++;
                endDay++;
            }
            if ((dateTime.getDayOfYear() < startDay) ||
                    (dateTime.getDayOfYear() > endDay)) {
                return -1;
            }
            return dateTime.getDayOfYear() - startDay;
        }

        String getLabel(Hemisphere hemisphere) {
            return (hemisphere == Hemisphere.NORTHERN) ? nHemisphereLabel : sHemisphereLabel;
        }

        String getValue(DateTime dateTime) {
            NumberFormat nf = new DecimalFormat("#0");
            if ((this == WINTER) || (this == WINTER2)) {
                if (this == WINTER) {
                    float result = ((float)daysInTheSeason(dateTime) /
                            duration(dateTime, this) + duration(dateTime, WINTER2)) * 100;
                    return nf.format(result);
                } else {
                    float result = ((float)(duration(dateTime, WINTER) + daysInTheSeason(dateTime)) /
                            duration(dateTime, this) + duration(dateTime, WINTER2)) * 100;
                    return nf.format(result);
                }
            } else {
                float result = ((float) daysInTheSeason(dateTime) / duration(dateTime, this)) * 100;
                return nf.format(result);
            }
        }

        static int duration(DateTime dateTime, Season season) {
            return (dateTime.year().isLeap())
                    ? (season.endDay - season.startDay) + 1
                    : (season.endDay - season.startDay);
        }

        static Season get(DateTime dateTime) {
            for (Season season : values()) {
                int dayOfYear = dateTime.getDayOfYear();
                int startDay = season.startDay;
                int endDay = season.endDay;
                if (dateTime.year().isLeap()) {
                    startDay++;
                    endDay++;
                }
                if ((dayOfYear >= startDay) && (dayOfYear <= endDay)) {
                    return season;
                }
            }
            return null;
        }
    }
}
