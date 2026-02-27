package com.billbill2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;



@Controller
public class GetVideoController {

    @GetMapping("toGetVideo")
    public String toGetVideo(){return "GetVideo";}

}
