package com.xiaoli.yunpicturebackend.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取以图搜图的图片页面地址
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {

        //form-data; name="image" https%3A%2F%2Fyun-picture-1425973504.cos.ap-nanjing.myqcloud.com%2Fspace%2F2093620598119907330%2F2026-08-29-j72qcRlR2bcCR1RS.webp
        //form-data; name="tn" pc
        //form-data; name="from"pc
        //form-data; name="image_source" PC_UPLOAD_URL
        //form-data; name="sdkParams"
        //{"data":"cc4cf4ec787ba64c3afd4832cab30ee6ef54935b979675157f74321c77ccbd704f167c6a44b8c4c5842226f91f3aad8bd3f80b4309203aef45ddd4628456387836ce2c5d14c1adb14db3295d7df7a774e50bd31b04781fa2402d9fc8815873d711799991c7374227f11c61ddffd06584ae4dcc4bef4d01bbbfa55288bb60e115a605cc64e6457daaa6628a1685088fcd","key_id":"23","sign":"50c72ccd"}
        //请求头
        //Acs-Token:1788066915180_1788081369436_Pyedq9ZH0CXTgUAfFlQC73Yr+vIopMpKS0LGpdsw8wJWvnvtqhABEKJ2J970fKBR48XyJ9WAFXuNeVs6wBCgoAcZdxhi7idY5wYAmNEgY9kSTYTi+6Wxg4LSueu6g7L6I6elPJogsx+P8ANXoSuICI/UYllZ6MQ85c9LXuABJNYVXC8UqYSsT5ywQ9zQrrVv3sPNP4wNnM6iKbUs0K0eoGArHLBnY4tW0ok7yG+wvf2xMnm8eB5YSjYjoqlSUzZNnTIrIUdKiHhQMtWY748ZzLB2kAvIN9AUlAEIIhMxE2Gla0Ue+UUq8oePiJgDoylcgMiwW3/7ZUq7s+y3RV6Vm8QgYmh/FM3pzV+7jEuE+zN8lio7UqMwZmW+zQFaQs9ROUKzczLcBEJx/GW/HLUMpqioXstEOt13c8QZxzu2VtchetfpmHxehetJTerm0IHg
        // 1. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        //获取当前时间
        long uptime = System.currentTimeMillis();
        //请求地址
        //https://graph.baidu.com/upload?uptime=1788081369420
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        //发送请求
       try {
           HttpResponse httpResponse = HttpRequest.post(url)
                   .header("Acs-Token","")
                   .form(formData)
                   .timeout(5000)
                   .execute();
           if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
               throw  new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
           }
           //解析响应
           //status	0
           //msg	"Success"
           //data
           //url	"https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&f=all&isLogoShow=1&session_id=18222785225857929944&sign=1264eaca23de445666aef01788080078&tpl_from=pc"
           //sign	"1264eaca23de445666aef01788080078"
           String body = httpResponse.body();
           Map<String, Object> result = JSONUtil.toBean(body, Map.class);

           //3.处理响应结果
           if(result == null||!Integer.valueOf(0).equals(result.get("status"))){
               throw  new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
           }
           Map<String, Object> data = (Map<String, Object>) result.get("data");
           //对原生url进行解码(如转义符等)，获取搜索结果的url
           String rawUrl = (String) data.get("url");
           String searchResultUrl = URLUtil.decode(rawUrl,StandardCharsets.UTF_8);
           //如果searchResultUrl为空，说明没有搜索到图片
           if (StrUtil.isBlank(searchResultUrl)) {
               throw  new BusinessException(ErrorCode.OPERATION_ERROR,"未返回有效的结果地址");
           }

           return searchResultUrl;
       }catch (Exception e){
           log.error("调用百度以图搜图接口失败",e);
           throw  new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败");
       }
    }
    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://yun-picture-1425973504.cos.ap-nanjing.myqcloud.com/space/2093620598119907330/2026-08-29-j72qcRlR2bcCR1RS.webp";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}

