com.billbill2
 controller
  GetCommentController的
     getComment通过调用CommentCrawlerService.crawlAndSave实现爬取B站评论并评论入库功能
  ResponsePyComment的@RequestMapping("/api/bilibili/comment")作为前缀，localhost:8080/api/bilibili/comment/xx为方法前缀
     被python脚本getComment.py使用POST请求调用，发送JSON再此解析并真正入库，使用commentService.bathInsertComment将
     列表入库
dao
 CommentDao使用了mybatisplus的BaseMapper，在不编写一条SQL语句的情况下实现了评论批量入库（仅限简单SQL）
DTO
 使用lombok自动实现get/set方法，注意驼峰命名不然不会生成对应的get/set方法，提供给controller类使用作为前端传入数据的接收类
 entity
  Comment作为实体类，为其他类提供了评论的定义，与评论表comment关联，其中BvNum好像不太好自动生成，用TableFile注解强制绑
service
 impl为对应的实现方法CommentCrawlerServiceImpl用processBuilder和process，新建线程调用python脚本getComment.py
 而CommentServiceImpl接收getComment.py的POST请求，每一百条评论批量入库一次并返回入库条数给getComment.py来对账
 其他对应为service的抽象方法
Util已废弃，可以查看billbill1的解释
PyUtil
 getComment.py，作为爬虫脚本，实现了从B站爬取评论，将情况打印输出到命令行被java用BufferedReader，InputStreamReader接收
 爬取单页评论并加入列表尾部，调用batch_save_to_springboot用POST请求发送JSON格式的数据，由ResponsePyComment处理并入库
技术细节请看test或文档中的注解，


