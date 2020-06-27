package com.atguigu.gulimall.search.controller;

import com.atguigu.gulimall.search.service.GulimallSearchService;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;

public class SearchController {
    @Resource
        private GulimallSearchService gulimallSearchService;

    @GetMapping("/list.html")
    public String listPage(SearchParam searchParam, Model model) {
        SearchResult result = gulimallSearchService.search(searchParam);
        model.addAttribute("result", result);
        return "list";
    }
}
