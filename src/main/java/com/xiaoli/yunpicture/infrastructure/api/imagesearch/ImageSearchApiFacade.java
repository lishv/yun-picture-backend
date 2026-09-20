package com.xiaoli.yunpicture.infrastructure.api.imagesearch;

import com.xiaoli.yunpicture.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.xiaoli.yunpicture.infrastructure.api.imagesearch.sub.GetImageFirstUrlApi;
import com.xiaoli.yunpicture.infrastructure.api.imagesearch.sub.GetImageListApi;
import com.xiaoli.yunpicture.infrastructure.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        //String imageUrl = "https://yun-picture-1425973504.cos.ap-nanjing.myqcloud.com/public/2084640382689312770/2026-08-26-09A5XNtsEzGe7H4h.webp";
        String imageUrl = "https://img.huilingyun.asia/space/2093620598119907330/2026-08-29-j72qcRlR2bcCR1RS.webp";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}