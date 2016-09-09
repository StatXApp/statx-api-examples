/**
 * Copyright 2016 StatX Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

using System;
using IO.StatX.Api;
using IO.StatX.Client;
using IO.StatX.Model;
using IO.StatX;

namespace StatXAPIExamples
{

	/**
	 * Example to show how to update a number stat in StatX.
	 * The class updates a number stat with the countdown of the number of days
	 * from the current date to a given target date.
	 *
	 * Prerequisite: Download the app from the appstore (IOS) or playstore (android) and sign up.
	 *
	 * Run this program
	 */
	class MainClass
	{

		public static void Main(string[] args)
		{
			StatXClient statxClient = new StatXClient();

			Configuration configuration = statxClient.getConfiguration();

			Console.WriteLine("Enter Target Date in mm/dd/yyy format:");

			string targetDate = Console.ReadLine();

			Console.WriteLine("Enter Stat Title");

			string statTitle = Console.ReadLine();


			// Repeat once every 24 hours (see parameter below).
			while (true)
			{
				string groupName = "StatX-API-Examples";
				GroupsApi groupsApi = new GroupsApi(configuration);
				GroupList groupList = groupsApi.GetGroups(groupName);
				Group group;
				if ((groupList == null) || (groupList.Data == null) || (groupList.Data.Count == 0))
				{
					// The group does not exist. Let's create one. Since we are creating the group
					// the api will add the current user as a member and admin of the group.
					group = new Group(null, groupName, null);
					group = groupsApi.CreateGroup(group);
				}
				else {
					// Pick the first group (should be the only one).
					group = groupList.Data[0];
				}


				// Find the stat by name. If the stat does not exist then create it.
				//
				// Note: The stat title is not unique. In general it is not a good idea to use
				// the stat title as a key to determine whether the stat exists or not. If possible
				// use the statid instead.
				StatsApi statsApi = new StatsApi(configuration);
				StatList statList = statsApi.GetStats(group.Name, statTitle);
				if ((statList == null) || (statList.Data == null) || (statList.Data.Count == 0))
				{
					// The stat does not exist. Let's create a number stat.
					NumberStat numberStat = new NumberStat();
					numberStat.Title = statTitle;
					numberStat.VisualType = Stat.VisualTypeEnum.Number;
					numberStat.Value = getRemainingDays(targetDate);
					statsApi.CreateStat(group.Id, numberStat);
				}
				else {
					// Pick the first stat (should be the only one) and get the statId from it.
					string statId = statList.Data[0].Id;

					// Create the stat to update.
					NumberStat numberStat = new NumberStat();
					numberStat.Value = getRemainingDays(targetDate);
					numberStat.LastUpdatedDateTime = DateTime.Now;
					statsApi.UpdateStat(group.Id, statId, numberStat);
				}

				Console.WriteLine("Last update at: " + DateTime.Now);

				System.Threading.Thread.Sleep(TimeSpan.FromDays(1));

			}

		}

		private static string getRemainingDays(string targetDate)
		{
			DateTime targetDateTime = Convert.ToDateTime(targetDate);

			DateTime currentCurrentDate = DateTime.Now;
			double remainingDays = (targetDateTime - currentCurrentDate).TotalDays;
			return Convert.ToString(Convert.ToInt64(remainingDays)); 
		}

	}
}
