using System;
using System.Collections.Generic;
using IO.StatX.Api;
using IO.StatX.Client;
using IO.StatX.Model;
using IO.StatX;
using RestSharp;
using Newtonsoft.Json.Linq;

/**
 * Copyright 2016 StatX Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

namespace Stock
{
	class MainClass
	{

		public static void Main(string[] args)
		{

			StatXClient statxClient = new StatXClient();

			Configuration configuration = statxClient.getConfiguration();

			Console.WriteLine("Enter Stat Title");

			string statTitle = Console.ReadLine();

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
				HorizontalBarStat horizontalBar = new HorizontalBarStat();
				horizontalBar.Title = statTitle;
				horizontalBar.VisualType = Stat.VisualTypeEnum.HorizontalBars;
				horizontalBar.Items = getStockInfo();
				statsApi.CreateStat(group.Id, horizontalBar);
			}
			else {
				// Pick the first stat (should be the only one) and get the statId from it.
				string statId = statList.Data[0].Id;
				HorizontalBarStat horizontalBarStat = (HorizontalBarStat) statList.Data[0];

				// Create the stat to update.
				HorizontalBarStat newHorizontalBardStat = new HorizontalBarStat();
				newHorizontalBardStat.Items = getStockInfo();
				newHorizontalBardStat.LastUpdatedDateTime = DateTime.Now;
				statsApi.UpdateStat(group.Id, statId, newHorizontalBardStat);
			}

			Console.WriteLine("Last update at: " + DateTime.Now);

		}


		public static List<HorizontalBarItem> getStockInfo()
		{
			var client = new RestClient("https://query.yahooapis.com");

			var request = new RestRequest("v1/public/yql", Method.GET);
			request.AddParameter("q", "select * from yahoo.finance.quotes where symbol in (\"AAPL\", \"AMZN\", \"GOOGL\")"); 
			request.AddParameter("format", "json");
			request.AddParameter("env", "store://datatables.org/alltableswithkeys");

			// execute the request
			IRestResponse response = client.Execute(request);
			var content = response.Content; // raw content as string

			JObject queryResult = JObject.Parse(content);

			JArray quotes = (JArray)queryResult["query"]["results"]["quote"];

			List<HorizontalBarItem> items = new List<HorizontalBarItem>();

			foreach (var quote in quotes.Children())
			{
				items.Add(new HorizontalBarItem((string)quote["Ask"], 
				                                HorizontalBarItem.ColorEnum.Green, (string)quote["symbol"]));
			}

			return items;
		}
	}
}
