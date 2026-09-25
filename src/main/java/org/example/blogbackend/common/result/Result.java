package org.example.blogbackend.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    //私有构造器
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    //+静态工厂
    private static <T> Result<T> success(){
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),null);
    }
    private static <T> Result<T> success(T data){
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),data);
    }
    private static <T> Result<T> error(ResultCode resultCode){             //基于枚举，错误是系统预先定义好的标准错误
        return new Result<>(resultCode.getCode(),resultCode.getMessage(),null);
    }
    private static <T> Result<T> error (int  code, String message){        //自定义返回错误信息
        return new Result<>(code,message,null);
    }
}
