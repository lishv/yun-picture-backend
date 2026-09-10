package com.xiaoli.yunpicture.infrastructure.api.imagesearch.sub;

import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GetImageFirstUrlApi {

    /**
     * 获取图片列表页面地址api
     *
     * @param url
     * @return
     */
    public static String getImageFirstUrl(String url) {
        try {
            //使用Jsoup获取Html内容
            Document document = Jsoup.connect(url).timeout(10000).get();
            //获取所有<script>标签
            Elements scriptElements = document.getElementsByTag("script");
            // 遍历找到包含 "firstUrl"的脚本内容
            for(Element script : scriptElements){
                String scriptContent = script.html();
                if (scriptContent.contains("\"firstUrl\"")){
                    // 正则表达式提取 firstUrl 的值
                    //取"firstUrl:"后中第一个""内的值
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);
                    //find() 方法用于在字符串中查找符合正则表达式条件的子序列。找到了则返回 true
                    if(matcher.find()){
                        // group(1) 获取正则表达式中第一个捕获组的内容，即括号 (.*?) 捕获到的 URL 值
                        String firstUrl = matcher.group(1);
                        //处理转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }
                }
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到 url");
        } catch (IOException e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 请求目标 URL
        String url = "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData[isLogoShow]=1&f=all&isLogoShow=1&session_id=4256768970646610624&sign=12645aca23de445666aef01788089534&tpl_from=pc";
        String imageFirstUrl = getImageFirstUrl(url);
        System.out.println("搜索成功，结果 URL：" + imageFirstUrl);
    }
}