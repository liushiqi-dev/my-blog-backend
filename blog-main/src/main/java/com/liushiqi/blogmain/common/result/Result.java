package com.liushiqi.blogmain.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Result，API通用响应结构
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    /**
     * 响应码，1 表示成功，2 表示失败
     */
    private Integer code;
    /**
     * 响应信息，接口返回的状态描述信息
     */
    private String message;
    /**
     * 返回数据，接口返回的业务数据主体
     */
    private Object data;

// 成功响应，不包含数据
    public static Result success() {
        return new Result(1,"success",null);
    }

    // 成功响应，包含数据
    public static Result success(Object data) {
        return new Result(1,"success",data);
    }

// 失败响应
    public static Result error(String message) {
        return new Result(2,message,null);
    }
}