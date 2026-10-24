package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import lombok.Data;

@Data
public class PictureDeleteMessage {

    private Long taskId;

    private Long pictureId;

    private String pictureName;

    private String url;

    private String thumbnailUrl;

    private Long pictureUserId;

    private Long spaceId;

    private Long operatorId;

    private String operatorName;
}