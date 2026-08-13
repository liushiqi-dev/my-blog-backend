package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.dto.request.CategoryRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper {

    void insert(CategoryRequest req);
}
