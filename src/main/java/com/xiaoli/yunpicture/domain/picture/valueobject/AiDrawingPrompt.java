package com.xiaoli.yunpicture.domain.picture.valueobject;

/**
 * AI 绘图提示词模板。
 */
public final class AiDrawingPrompt {

    public static final int MAX_USER_PROMPT_LENGTH = 4800;

    private static final String PREFIX = """
            你正在执行基于参考图片的定向图像编辑任务。必须遵循以下规则：
            1. 除非用户明确要求，否则保留原图主体身份、核心外观特征和主要构图。
            2. 只修改用户明确要求的内容，不得擅自改变、删除或替换无关主体。
            3. 未指定区域应尽量保持原样；新增或修改内容必须与原图的风格、色彩、光照、透视和清晰度自然融合。
            4. 输出一张完整、清晰的图片，不要增加用户未要求的文字或水印。
            用户要求：
            """;

    private AiDrawingPrompt() {
    }

    public static String build(String userPrompt) {
        return PREFIX + userPrompt.trim();
    }
}
