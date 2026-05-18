package com.example.community.im.dto;

import lombok.Data;

@Data
public class ImMessageDTO {

    /** 已有会话ID（已知时传，否则传 receiverId） */
    private Long conversationId;

    /** 单聊对端，创建新会话时使用 */
    private Long receiverId;

    /** 1-文字  2-图片 */
    private Integer type;

    /** 文字内容 */
    private String content;

    /** 图片URL（先通过 /api/upload 上传拿到URL再发消息） */
    private String imageUrl;

    /** 客户端生成的唯一ID（UUID），用于幂等去重和ACK对应 */
    private String clientMsgId;

    /**
     * 控制消息类型，为 null 表示普通数据消息。
     * 当前支持：recv_ack（接收方告知服务端消息已到达应用层）
     */
    private String controlType;

    /** recv_ack 时填写被确认消息的 seq，与 conversationId 联合定位具体消息 */
    private Long seq;
}
