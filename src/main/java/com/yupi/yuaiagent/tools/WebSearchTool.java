package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 * 使用 SerpAPI（每月免费100次调用额度）
 */
public class WebSearchTool {

    private static final String SERPAPI_URL = "https://serpapi.com/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from search engines")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "google");
        paramMap.put("hl", "zh-CN");
        paramMap.put("gl", "cn");
        try {
            String response = HttpUtil.get(SERPAPI_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "No search results found";
            }
            int limit = Math.min(5, organicResults.size());
            List<Object> objects = organicResults.subList(0, limit);
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                String title = tmpJSONObject.getStr("title", "");
                String snippet = tmpJSONObject.getStr("snippet", "");
                String link = tmpJSONObject.getStr("link", "");
                return String.format("Title: %s\nLink: %s\nSnippet: %s\n", title, link, snippet);
            }).collect(Collectors.joining("\n"));
            return result;
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }
}
