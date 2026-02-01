package com.billbill2.controller;

import com.billbill2.entity.Comment;
import com.billbill2.service.CommentService;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bilibili/comment")
@RequiredArgsConstructor
public class ResponsePyComment {
    private final CommentService commentService;

    // 时间格式化器（避免重复创建）
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /**
     * 批量入库接口（Python端调用此接口）
     * @param request 请求体：{"bvNum": "BVxxx", "commentList": [{"昵称": "xxx", "性别": "男", ...}]}
     */
    //被python的脚本调用，"http://localhost:8080/api/bilibili/comment/batch-save"实现评论入库
    @PostMapping("/batch-save")
    public ResponseEntity<Map<String, Object>> batchSave(@RequestBody Map<String, Object> request) {
        // 新建 HashMap 存储返回结果（替换 Map.of()）
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 1. 解析Python提交的参数
            String bvNum = (String) request.get("bvNum");
            List<Map<String, String>> commentList = (List<Map<String, String>>) request.get("commentList");

            // 2. 转换为实体类列表（映射Python的字段到Java实体）
            List<Comment> entityList = commentList.stream().map(commentMap -> {
                Comment comment = new Comment();
                comment.setName(commentMap.get("昵称")); // 对应Python的"昵称"
                comment.setSex(commentMap.get("性别"));   // 对应Python的"性别"
                comment.setArea(commentMap.get("地区"));   // 对应Python的"地区"
                comment.setComment(commentMap.get("评论")); // 对应Python的"评论"
                comment.setRpid(commentMap.get("rpid")); //对应Python的"rpid"
                comment.setBvNum(bvNum); // 统一设置BV号
                return comment;
            }).collect(Collectors.toList());

            // 3. 调用Service批量入库
            int successCount = commentService.batchUpsertComments(entityList);

            // 4. 返回成功结果（和Python端的result.get("code")匹配）
            // 成功结果：用 HashMap.put() 替代 Map.of()
            resultMap.put("code", 200);
            resultMap.put("msg", "批量入库成功");
            resultMap.put("successCount", successCount);

            //新增,为前端提供下载CSV提供BV号
            resultMap.put("BvNum",bvNum);
        } catch (Exception e) {
            // 5. 异常返回（Python端会捕获此错误信息）
            // 失败结果：用 HashMap.put() 替代 Map.of()
            resultMap.put("code", 500);
            resultMap.put("msg", "批量入库失败：" + e.getMessage());
            resultMap.put("successCount", 0);
        }
        return ResponseEntity.ok(resultMap);
    }

    @GetMapping("/downloadCommentCSV")
    public ResponseEntity<byte[]> downloadCommentCsv(@RequestParam String bvNum){
        try{
            List<Comment> commentList=commentService.getCommentByBvNum(bvNum);
            if(commentList.isEmpty()){
                return ResponseEntity.badRequest().body(("未查询到BV号[" + bvNum + "]的评论数据").getBytes(StandardCharsets.UTF_8));
            }

            //动态生成CSV字节流，不需要下载到服务器本地
            ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
            //使用UTF8避免中文乱码
            try(OutputStreamWriter osw=new OutputStreamWriter(outputStream,StandardCharsets.UTF_8);
                CSVWriter csvWriter=new CSVWriter(osw,
                    CSVWriter.DEFAULT_SEPARATOR,
                    CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END)){
                // 核心修复1：写入 UTF-8 BOM 头（告诉Excel/浏览器这是UTF-8编码）
                outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                //写入CSV表头
                csvWriter.writeNext(new String[]{"name","sex","area","comment","BvNum"});
                //写入评论数据
                for(Comment comment:commentList){
                    csvWriter.writeNext(new String[]{
                            comment.getName(),
                            comment.getSex(),
                            comment.getArea()==null?"":comment.getArea(),
                            comment.getComment(),
                            comment.getBvNum()
                    });
                }
                csvWriter.flush();
            }//end intry
            HttpHeaders headers=new HttpHeaders();
            String fileName=bvNum+"_评论数据_"+System.currentTimeMillis()+".csv";
            //文件名编码:UTF-8转ISO适配浏览器下载
            String encodedFileName=new String(fileName.getBytes(StandardCharsets.UTF_8),StandardCharsets.ISO_8859_1);
            headers.setContentDispositionFormData("attachment", encodedFileName);
            // 指定响应体编码为 UTF-8
            headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
            headers.setContentLength(outputStream.size());
            return new ResponseEntity<>(outputStream.toByteArray(),headers, HttpStatus.OK);
        }//end outtry
        catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("CSV生成失败"+e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
