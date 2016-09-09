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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.statx.rest.StatXClient;
import io.statx.rest.api.GroupsApi;
import io.statx.rest.api.StatsApi;
import io.statx.rest.model.*;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Example to show how to create/update a horizontal bars stat through the StatX REST API.
 */
public class StockExample {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage java io.statx.examples.StockExample <ClientName> " +
                    "<PhoneNumber Int Format> <StatTitle> <FrequencyInMinutes>");
            System.exit(-1);
        }
        String clientName = args[0];
        String phoneNumber = args[1];
        String statTitle = args[2];
        int frequencyInMinutes = Integer.parseInt(args[3]);

        // Lets sign up through the rest API and get an AuthToken. Once you get the credentials
        // you should save them somewhere safe for use at a later time.
        StatXClient statXClient = new StatXClient();
        StatXClient.UserCredential userCredential = statXClient.getCredentials(clientName, phoneNumber);

        // Repeat once every <frequency minutes> (see parameter below).
        while (true) {

            // Find the group with the stat. If the group does not exist then create it.
            //
            // Note: The group name is not unique. In general it is not a good idea to use the group
            // name as a key to determine whether the group exists or not. If possible use the
            // groupid instead.
            String groupName = "StatX-API-Examples";
            GroupsApi groupsApi = statXClient.getGroupsApi(userCredential);
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
            StatsApi statsApi = statXClient.getStatsApi(userCredential);
            StatList statList = statsApi.getStats(group.getName(), statTitle);
            if ((statList == null) || (statList.getData() == null) || (statList.getData().isEmpty())) {
                // The stat does not exist. Let's create it.
                HorizontalBarStat horizontalBarStat = new HorizontalBarStat();
                horizontalBarStat.setTitle(statTitle);
                horizontalBarStat.setVisualType(Stat.VisualTypeEnum.HORIZONTAL_BARS);
                horizontalBarStat.setGroupName(groupName);
                horizontalBarStat.setItems(getStockInfo());
                statsApi.createStat(group.getId(), horizontalBarStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Create the stat to update the value.
                HorizontalBarStat horizontalBarStat = new HorizontalBarStat();
                horizontalBarStat.setItems(getStockInfo());
                horizontalBarStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, horizontalBarStat);
            }

            System.out.println("Last update at: " + new Date(System.currentTimeMillis()));
            Thread.sleep(TimeUnit.MINUTES.toMillis(frequencyInMinutes));
        }
    }


    /**
     * Fetch a few stocks from Yahoo finance.
     * @return a {@code List<HorizontalBarItem} with the details of the stock prices.
     * @throws IOException
     * @throws URISyntaxException
     */
    private static List<HorizontalBarItem> getStockInfo() throws IOException, URISyntaxException {
        List<HorizontalBarItem> results = new ArrayList<>();
        int TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setConnectionRequestTimeout(TIMEOUT_MILLIS)
                .setSocketTimeout(TIMEOUT_MILLIS).build();
        HttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(10)
                .build();
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https").setHost("query.yahooapis.com").setPath("/v1/public/yql");
        uriBuilder.addParameter("q", "select * from yahoo.finance.quotes where symbol in(" +
                "\"AAPL\", \"AMZN\",\"GOOGL\")");
        uriBuilder.addParameter("format","json");
        uriBuilder.addParameter("env", "store://datatables.org/alltableswithkeys");
        URI uri = uriBuilder.build();
        HttpGet httpGet = new HttpGet(uri);
        httpGet.addHeader("content-type", ContentType.APPLICATION_JSON.toString());
        HttpResponse httpResponse = httpClient.execute(httpGet);
        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            Gson gson = new Gson();
            JsonObject job = gson.fromJson(
                    new InputStreamReader(httpResponse.getEntity().getContent()), JsonObject.class);
            JsonArray jsonArray = job.getAsJsonObject("query").getAsJsonObject("results").getAsJsonArray("quote");
            int colorIndex;
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement jsonElement = jsonArray.get(i);
                HorizontalBarItem horizontalBarItem = new HorizontalBarItem();
                horizontalBarItem.setName(((JsonObject) jsonElement).get("symbol").getAsString());
                horizontalBarItem.setValue(((JsonObject) jsonElement).get("Ask").getAsString());
                colorIndex = i % HorizontalBarItem.ColorEnum.values().length;
                horizontalBarItem.setColor(HorizontalBarItem.ColorEnum.values()[colorIndex]);
                results.add(horizontalBarItem);
            }
        }
        return results;
    }
}
