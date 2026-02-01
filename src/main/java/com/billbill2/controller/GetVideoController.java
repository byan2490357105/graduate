package com.billbill2.controller;

import com.billbill2.service.CrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class GetVideoController {

    @GetMapping("toGetVideo")
    public String toGetVideo(){return "GetVideo";}
}
