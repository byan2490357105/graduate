package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.CommentDao;
import com.billbill2.entity.Comment;
import com.billbill2.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentDao, Comment> implements CommentService {
    private Integer commentListNum=100;

    @Autowired
    private CommentDao commentDao;
    
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到",
        "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "他", "她", "它", "这个", "那个",
        "什么", "怎么", "为什么", "哪", "哪里", "哪个", "吗", "呢", "吧", "啊", "呀", "哦", "嗯", "哈", "么", "嘛",
        "但", "但是", "而", "而且", "或", "或者", "又", "还", "更", "最", "太", "非常", "真", "真是", "真的",
        "可以", "能", "能够", "应该", "得", "让", "被", "把", "跟", "比", "对", "向", "从", "给", "与", "及",
        "个", "些", "每", "各", "某", "本", "该", "其", "此", "彼", "另", "别", "其他", "余下",
        "这", "那", "这些", "那些", "这里", "那里", "这时", "那时", "这样", "那样", "这么", "那么",
        "已", "已经", "曾", "曾经", "正", "正在", "将", "将要", "刚", "刚刚", "才", "刚才",
        "就", "便", "于是", "然后", "接着", "随后", "最后", "终于", "结果", "所以", "因此", "因为", "由于",
        "虽然", "尽管", "即使", "哪怕", "无论", "不管", "除非", "只有", "只要", "假如", "如果", "要是",
        "而且", "并且", "不但", "不仅", "不光", "何况", "况且", "反而", "相反", "不过", "只是", "可惜",
        "哈哈", "哈哈哈", "哈哈哈哈", "呵呵", "嘿嘿", "嘻嘻", "额", "嗯", "啊", "呢", "吧", "哦", "呀",
        "回复", "视频", "BV", "bv", "UP", "up", "主", "up主", "UP主", "投币", "点赞", "收藏", "分享", "转发",
        "评论", "弹幕", "关注", "粉丝", "关注", "订阅", "三连", "一键三连", "下次一定", "下次",
        "www", "WWW", "com", "http", "https", "html", "jpg", "png", "gif", "mp4", "avi",
        "觉得", "感觉", "知道", "以为", "觉得是", "就是", "不是", "还是", "只是", "也是", "都是",
        "这个", "那个", "哪个", "怎么", "这样", "那样", "怎样", "这么", "那么", "多么",
        "一直", "一定", "一起", "一下", "一些", "一点", "一会儿", "一下子",
        "可能", "应该", "必须", "需要", "想要", "希望", "喜欢", "爱", "想", "要",
        "时候", "地方", "东西", "事情", "样子", "方面", "问题", "原因", "结果",
        "大家", "我们", "你们", "他们", "她们", "它们", "咱们", "自己", "别人",
        "现在", "今天", "昨天", "明天", "以后", "之前", "最近", "之前", "之后",
        "第一", "第二", "第三", "最后", "首先", "然后", "接着", "最后",
        "很多", "多少", "几个", "一些", "所有", "全部", "部分", "大部分",
        "其实", "确实", "实在", "真的", "真正", "确实", "确实",
        "起来", "出来", "进来", "回来", "过来", "进去", "出去", "回去", "过去",
        "开始", "结束", "继续", "停止", "完成", "进行"
    ));
    
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpsertComments(List<Comment> commentList) {
        if (commentList == null || commentList.isEmpty()) {
            return 0;
        }
        boolean isSuccess = commentDao.batchUpsertComments(commentList);
        return isSuccess ? commentList.size() : 0;
    }

    @Override
    public List<Comment> getCommentByBvNum(String BvNum) {
        return commentDao.selectByBvNum(BvNum);
    }

    @Override
    public Map<String, Integer> getCommentWordStatistics(int limit) {
        log.info("开始统计评论热词，限制返回数量：{}", limit);
        
        Map<String, Integer> wordCountMap = new HashMap<>();
        
        int batchSize = 2000;
        int current = 0;
        int processedCount = 0;
        
        try {
            while (true) {
                LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.select(Comment::getComment)
                           .isNotNull(Comment::getComment)
                           .last("LIMIT " + current + ", " + batchSize);
                
                List<Comment> comments = this.list(queryWrapper);
                
                if (comments == null || comments.isEmpty()) {
                    break;
                }
                
                for (Comment comment : comments) {
                    String content = comment.getComment();
                    if (content != null && !content.trim().isEmpty()) {
                        processCommentContent(content, wordCountMap);
                    }
                }
                
                processedCount += comments.size();
                current += batchSize;
                
                if (processedCount % 20000 == 0) {
                    log.info("已处理{}条评论，当前词库大小：{}", processedCount, wordCountMap.size());
                    System.gc();
                }
            }
            
            log.info("评论处理完成，共处理{}条，词库大小：{}", processedCount, wordCountMap.size());
            
            Map<String, Integer> sortedMap = wordCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
            
            log.info("返回前{}个热词", sortedMap.size());
            return sortedMap;
            
        } catch (Exception e) {
            log.error("统计评论热词失败", e);
            return new HashMap<>();
        }
    }
    
    private void processCommentContent(String content, Map<String, Integer> wordCountMap) {
        try {
            content = content.replaceAll("[\\p{Punct}\\p{S}\\p{C}\\s\\d]+", " ");
            content = content.replaceAll("[a-zA-Z]+", " ");
            content = content.trim();
            
            if (content.isEmpty()) {
                return;
            }
            
            Matcher matcher = CHINESE_PATTERN.matcher(content);
            while (matcher.find()) {
                String segment = matcher.group();
                extractWords(segment, wordCountMap);
            }
        } catch (Exception e) {
            log.debug("分词处理失败：{}", content);
        }
    }
    
    private void extractWords(String text, Map<String, Integer> wordCountMap) {
        int len = text.length();
        
        for (int wordLen = 4; wordLen >= 2; wordLen--) {
            for (int i = 0; i <= len - wordLen; i++) {
                String word = text.substring(i, i + wordLen);
                
                if (STOP_WORDS.contains(word)) {
                    continue;
                }
                
                if (isMeaningfulWord(word)) {
                    wordCountMap.put(word, wordCountMap.getOrDefault(word, 0) + 1);
                }
            }
        }
    }
    
    private boolean isMeaningfulWord(String word) {
        if (word == null || word.length() < 2) {
            return false;
        }
        
        int distinctChars = (int) word.chars().distinct().count();
        if (distinctChars < word.length() * 0.5) {
            return false;
        }
        
        return true;
    }
}
