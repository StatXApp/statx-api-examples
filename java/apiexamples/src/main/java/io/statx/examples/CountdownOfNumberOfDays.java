/**
 * Copyright 2016 StatX Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package io.statx.examples;

import io.statx.rest.StatXClient;
import io.statx.rest.api.GroupsApi;
import io.statx.rest.api.StatsApi;
import io.statx.rest.model.*;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Example to show how to update a number stat in StatX.
 * The class updates a number stat with the countdown of the number of days
 * from the current date to a given target date.
 *
 * Prerequisite: Download the StatX app from the appstore (IOS) or playstore (android) and sign up.
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
        StatXClient statXClient = new StatXClient();
        StatXClient.UserCredential userCredential = statXClient.getCredentials(clientName, phoneNumber);

        // Repeat once every 24 hours (see parameter below).
        while (true) {

            // Find the group with the countdown stat. If the group does not exist then create it.
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
                // The stat does not exist. Let's create a number stat.
                NumberStat numberStat = new NumberStat();
                numberStat.setTitle(statTitle);
                numberStat.setVisualType(Stat.VisualTypeEnum.NUMBER);
                numberStat.setGroupName(groupName);
                numberStat.rawValue(getRemainingDays(targetDate));
                statsApi.createStat(group.getId(), numberStat);
            } else {
                // Pick the first stat (should be the only one) and get the statId from it.
                String statId = statList.getData().get(0).getId();

                // Create the stat to update.
                NumberStat numberStat = new NumberStat();
                numberStat.rawValue(getRemainingDays(targetDate));
                numberStat.setLastUpdatedDateTime(new Date(System.currentTimeMillis()));
                statsApi.updateStat(group.getId(), statId, numberStat);
            }

            System.out.println("Last update at: " + new Date(System.currentTimeMillis()));
            Thread.sleep(TimeUnit.DAYS.toMillis(1));
        }
    }

    private static double getRemainingDays(DateTime targetDate) {
        DateTime currentDate = new DateTime(System.currentTimeMillis());
        return Days.daysBetween(currentDate, targetDate).getDays();
    }
}
