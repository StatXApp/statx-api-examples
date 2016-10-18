/**
 * Copyright 2016 StatX Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package io.statx.examples;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.statx.rest.ApiException;
import io.statx.rest.StatXClient;
import io.statx.rest.api.GroupsApi;
import io.statx.rest.api.StatsApi;
import io.statx.rest.model.*;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Sample code that shows how to create/update stats for your EV car.
 *
 * Prerequisite: Buy or borrow a Tesla.
 *               Download and install the StatX app
 *               from the AppStore (iOS) or PlayStore (Android).
 *
 * To build do:
 * mvn compile
 *
 * To run do:
 * mvn exec:java -Dexec.mainClass="io.statx.examples.TestStatus"
 *
 * It will keep updating the stats in the StatX app every 15 minutes.
 *
 * The TESLA REST API is unofficial and unsupported by Tesla.
 * 
 * The TESLA REST API code in this example is based on the documentation in the following site:
 * http://docs.timdorr.apiary.io/#reference/vehicles/state-and-settings/charge-state
 */
public class TeslaStatus {
    // These two are taken from the following site:
    // http://docs.timdorr.apiary.io/#reference/vehicles/state-and-settings/charge-state
    private static final String CLIENT_ID_FOR_TESLA_MOTORS =
            "e4a9949fcfa04068f59abb5a658f2bac0a3428e4652315490b659d5ab3f35a9e";
    private static final String CLIENT_SECRET_FOR_TESLA_MOTORS =
            "c75f14bbadc8bee3a7594412c31416f8300256d7668ea7e6e7f06727bfb9d220";

    private static final HttpClient httpClient = getHttpClient();
    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String GROUP_NAME = "Tesla Status";
    private static final String BATTERY_LEVEL_STAT_TITLE = "Battery Level";
    private static final String BATTERY_RANGE_STAT_TITLE = "Range";
    private static final String BATTERY_CHARGING_STAT_TITLE = "Charging State";

    private static final StatXClient statXClient = new StatXClient();
    public static final String SCHEME = "https";
    public static final String OWNER_API_TESLAMOTORS_COM = "owner-api.teslamotors.com";

    // After you downloaded and installed the StatX app use the GetUserCredentials sample code to get
    // your API_KEY and AUTH_TOKEN for the StatX REST API. Set those credentials here.
    private StatXClient.UserCredential userCredential =
            new StatXClient.UserCredential("<StatXAPIKey>", "<StatXAuthToken>");

    public static void main (String args[]) throws Exception {
        TeslaStatus TeslaStatus = new TeslaStatus();
        fromCli(TeslaStatus);
    }

    // Reads parameters from standard input. Masks the password.
    private static void fromCli(TeslaStatus TeslaStatus) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the db user email");
        String userEmail = scanner.next();
        System.out.println("Enter the pwd");
        Console console;
        char[] passwd;
        if ((console = System.console()) != null &&
                (passwd = console.readPassword("[%s]", "Password:")) != null) {
            String dbPwd = new String(passwd);
            TeslaStatus.update(userEmail, dbPwd);
            java.util.Arrays.fill(passwd, ' ');
        }
    }

    private void update(String userEmail, String password)
            throws IOException, URISyntaxException, InterruptedException, ApiException {
        StatsApi statsApi = statXClient.getStatsApi(userCredential);
        String authToken = getAuthToken(CLIENT_ID_FOR_TESLA_MOTORS, CLIENT_SECRET_FOR_TESLA_MOTORS,
                GRANT_TYPE_PASSWORD, userEmail, password);
        if (!authToken.isEmpty()) {
            String vehicleId = getVehicleId(authToken);
            if (!vehicleId.isEmpty()) {
                while (true) {
                    GroupsApi groupsApi = statXClient.getGroupsApi(userCredential);
                    GroupList groupList = groupsApi.getGroups(GROUP_NAME);
                    Group group;
                    if ((groupList == null) || (groupList.getData() == null) || (groupList.getData().isEmpty())) {
                        // The group does not exist. Let's create one. Since we are creating the group
                        // the api will add the current user as a member and admin of the group.
                        group = new Group();
                        group.setName(GROUP_NAME);
                        group = groupsApi.createGroup(group);
                    } else {
                        // Pick the first group (should be the only one).
                        group = groupList.getData().get(0);
                    }

                    BatteryDetails batteryDetails = getVehicleChargeState(authToken, vehicleId);
                    if (batteryDetails != null) {
                        // Update the 3 stats.
                        updateBatteryLevel(statsApi, group, batteryDetails.getBatteryLevel());

                        updateBatteryRange(statsApi, group, batteryDetails.getBatteryRange(),
                                batteryDetails.getIdealBatteryRange());

                        updateBatteryChargingState(statsApi, group, batteryDetails.getChargingState());
                    }
                    System.out.println("Last update at: " + new Date(System.currentTimeMillis()));
                    // Update the stats every 15 minutes.
                    Thread.sleep(TimeUnit.MINUTES.toMillis(15));
                }
            }
        }
    }

    private void updateBatteryLevel(StatsApi statsApi, Group group, String batteryLevel)
            throws ApiException {
        StatList statList = statsApi.getStats(group.getName(), BATTERY_LEVEL_STAT_TITLE);
        if (batteryLevel != null) {
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create it.
                DialerStat dialerStat = new DialerStat();
                dialerStat.setTitle(BATTERY_LEVEL_STAT_TITLE);
                dialerStat.setVisualType(Stat.VisualTypeEnum.DIALER);
                dialerStat.setGroupName(GROUP_NAME);
                dialerStat.setValue(batteryLevel);
                statsApi.createStat(group.getId(), dialerStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Update the stat value.
                DialerStat dialerStat = new DialerStat();
                dialerStat.setValue(batteryLevel);
                dialerStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, dialerStat);
            }
        }
    }

    private void updateBatteryRange(StatsApi statsApi, Group group,
                                    String batteryRange, String idealBatteryRange)
            throws ApiException {
        StatList statList = statsApi.getStats(group.getName(), BATTERY_RANGE_STAT_TITLE);
        if (batteryRange != null) {
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create it.
                RangeStat rangeStat = new RangeStat();
                rangeStat.setTitle(BATTERY_RANGE_STAT_TITLE);
                rangeStat.setVisualType(Stat.VisualTypeEnum.RANGE);
                rangeStat.setGroupName(GROUP_NAME);
                rangeStat.setMinValue("0");
                if (idealBatteryRange != null) {
                    rangeStat.setMaxValue(idealBatteryRange);
                }
                rangeStat.setValue(batteryRange);
                statsApi.createStat(group.getId(), rangeStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Update the stat value.
                RangeStat rangeStat = new RangeStat();
                rangeStat.setValue(batteryRange);
                rangeStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, rangeStat);
            }
        }
    }

    private void updateBatteryChargingState(StatsApi statsApi, Group group, String chargingState)
            throws ApiException {
        StatList statList = statsApi.getStats(group.getName(), BATTERY_CHARGING_STAT_TITLE);
        if (chargingState != null) {
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create it.
                PicklistStat picklistStat = new PicklistStat();
                picklistStat.setTitle(BATTERY_CHARGING_STAT_TITLE);
                picklistStat.setLabel("");
                picklistStat.setVisualType(Stat.VisualTypeEnum.PICK_LIST);
                picklistStat.setGroupName(GROUP_NAME);
                picklistStat.setItems(getPicklistItems());
                picklistStat.setCurrentIndex(getPicklistIndex(chargingState));
                statsApi.createStat(group.getId(), picklistStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Update the stat value.
                PicklistStat picklistStat = new PicklistStat();
                picklistStat.setItems(getPicklistItems());
                picklistStat.setCurrentIndex(getPicklistIndex(chargingState));
                picklistStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, picklistStat);
            }
        }
    }

    private int getPicklistIndex(String state) {
        BatteryState batteryState = BatteryState.get(state);
        if (batteryState == null) {
            return 0; // Unknown
        }
        switch (batteryState) {
            case DISCONNECTED:
                return 1;
            case CHARGING:
                return 2;
            case COMPLETE:
                return 3;
            default:
                // Unknown
                return 0;
        }
    }

    private List<PicklistItem> getPicklistItems() {
        List<PicklistItem> results = new ArrayList<>();
        PicklistItem picklistItem = new PicklistItem();
        picklistItem.setName(BatteryState.UNKNOWN.getLabel());
        picklistItem.setColor(PicklistItem.ColorEnum.GRAY);
        results.add(picklistItem);

        picklistItem = new PicklistItem();
        picklistItem.setName(BatteryState.DISCONNECTED.getLabel());
        picklistItem.setColor(PicklistItem.ColorEnum.RED);
        results.add(picklistItem);

        picklistItem = new PicklistItem();
        picklistItem.setName(BatteryState.CHARGING.getLabel());
        picklistItem.setColor(PicklistItem.ColorEnum.ORANGE);
        results.add(picklistItem);

        picklistItem = new PicklistItem();
        picklistItem.setName(BatteryState.COMPLETE.getLabel());
        picklistItem.setColor(PicklistItem.ColorEnum.GREEN);
        results.add(picklistItem);
        return results;
    }

    private String getAuthToken(String clientId, String clientSecret,
                                String grantType, String email, String password)
            throws URISyntaxException, IOException {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder
                .setScheme(SCHEME)
                .setHost(OWNER_API_TESLAMOTORS_COM)
                .setPath("/oauth/token")
                .addParameter("client_id", clientId)
                .addParameter("client_secret", clientSecret)
                .addParameter("grant_type", grantType)
                .addParameter("email", email)
                .addParameter("password", password);
        String result = "";
        HttpResponse httpResponse = executePostRequest(uriBuilder);
        try {
            if (httpResponse != null) {
                Gson gson = new Gson();
                JsonObject job = gson.fromJson(
                        new InputStreamReader(httpResponse.getEntity().getContent()), JsonObject.class);
                result = job.get("access_token").getAsString();
            }
            return result;
        } finally {
            closeResponse(httpResponse);
        }
    }

    private String getVehicleId(String authToken)
            throws URISyntaxException, IOException {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder
                .setScheme(SCHEME)
                .setHost(OWNER_API_TESLAMOTORS_COM)
                .setPath("/api/1/vehicles");
        String result = "";
        HttpResponse httpResponse = executeGetRequest(uriBuilder, authToken);
        try {
            if (httpResponse != null) {
                Gson gson = new Gson();
                JsonObject job = gson.fromJson(
                        new InputStreamReader(httpResponse.getEntity().getContent()), JsonObject.class);
                if (job != null) {
                    // For now we pick the first vehicle in the array.
                    result = job.getAsJsonArray("response").get(0).getAsJsonObject().get("id").getAsString();
                }
            }
            return result;
        } finally {
            closeResponse(httpResponse);
        }
    }

    private BatteryDetails getVehicleChargeState(String authToken, String vehicleId)
            throws URISyntaxException, IOException {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder
                .setScheme(SCHEME)
                .setHost(OWNER_API_TESLAMOTORS_COM)
                .setPath(String.format("/api/1/vehicles/%s/data_request/charge_state", vehicleId));

        BatteryDetails batteryDetails = null;
        HttpResponse httpResponse = executeGetRequest(uriBuilder, authToken);
        try {
            if (httpResponse != null) {
                Gson gson = new Gson();
                JsonObject job = gson.fromJson(
                        new InputStreamReader(httpResponse.getEntity().getContent()), JsonObject.class);
                JsonObject response = job.getAsJsonObject("response");
                String batteryLevel = getElementValue(response, "battery_level");
                String batteryRange = getElementValue(response, "battery_range");
                String idealBatteryRange = getElementValue(response, "ideal_battery_range");
                String chargingState = getElementValue(response, "charging_state");
                batteryDetails = new BatteryDetails(batteryLevel, batteryRange, idealBatteryRange, chargingState);
            }
            return batteryDetails;
        } finally {
            closeResponse(httpResponse);
        }
    }

    private String getElementValue(JsonObject response, String element) {
        JsonElement jsonElement = response.get(element);
        return jsonElement.isJsonNull() ? null : jsonElement.getAsString();
    }

    private void closeResponse(HttpResponse httpResponse) {
        if (httpResponse != null) {
            try {
                EntityUtils.consume(httpResponse.getEntity());
            } catch(IOException e) {
                // Should probably log the exception here.
            }
        }
    }

    private static HttpResponse executePostRequest(URIBuilder uriBuilder)
            throws URISyntaxException, IOException {
        String url = uriBuilder.build().toString();
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("content-type", ContentType.APPLICATION_JSON.toString());
        HttpResponse result = null;
        HttpResponse httpResponse = httpClient.execute(httpPost);
        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            result = httpResponse;
        }
        return result;
    }

    private static HttpResponse executeGetRequest(URIBuilder uriBuilder, String authToken)
            throws URISyntaxException, IOException {
        String url = uriBuilder.build().toString();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("content-type", ContentType.APPLICATION_JSON.toString());
        if (authToken != null) {
            httpGet.setHeader("Authorization", "Bearer " + authToken);
        }
        HttpResponse result = null;
        HttpResponse httpResponse = httpClient.execute(httpGet);
        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            result = httpResponse;
        }
        return result;
    }

    // Get the HttpClient.
    private static HttpClient getHttpClient() {
        int TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setConnectionRequestTimeout(TIMEOUT_MILLIS)
                .setSocketTimeout(TIMEOUT_MILLIS).build();

        ConnectionReuseStrategy connectionReuseStrategy = new ConnectionReuseStrategy() {
            @Override
            public boolean keepAlive(HttpResponse httpResponse, HttpContext httpContext) {
                return false;
            }
        };

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setConnectionReuseStrategy(connectionReuseStrategy)
                .setMaxConnTotal(5)
                .setMaxConnPerRoute(2)
                .build();
    }

    // Small class to keep the results.
    private static class BatteryDetails {
        String batteryLevel;
        String batteryRange;
        String idealBatteryRange;
        String chargingState;

        BatteryDetails(String batteryLevel, String batteryRange, String idealBatteryRange, String chargingState) {
            this.batteryLevel = batteryLevel;
            this.batteryRange = batteryRange;
            this.idealBatteryRange = idealBatteryRange;
            this.chargingState = chargingState;
        }

        String getBatteryLevel() {
            return batteryLevel;
        }

        String getBatteryRange() {
            return batteryRange;
        }

        String getIdealBatteryRange() {
            return idealBatteryRange;
        }

        String getChargingState() {
            return chargingState;
        }

        @Override
        public String toString() {
            return batteryLevel + " % " + batteryRange + " miles "
                    + idealBatteryRange + " miles " + chargingState;
        }
    }

    private enum BatteryState {
        DISCONNECTED("Disconnected"), CHARGING("Charging"),
        COMPLETE("Complete"), UNKNOWN("Unknown");

        private final String label;
        private static final Map<String, BatteryState> labelToEnum = new HashMap<>();

        static {
            labelToEnum.put(DISCONNECTED.getLabel(), DISCONNECTED);
            labelToEnum.put(CHARGING.getLabel(), CHARGING);
            labelToEnum.put(COMPLETE.getLabel(), COMPLETE);
            labelToEnum.put(UNKNOWN.getLabel(), UNKNOWN);
        }

        BatteryState(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        static BatteryState get(String label) {
            return labelToEnum.get(label);
        }
    }
}
