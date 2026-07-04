package com.logistics.mcp.tools.domain;

/** 用户信息（对应既有用户域微服务返回）。 */
public record User(long userId, String name, String phone, String level) {
}
