package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.dto.request.CategoryRequest;
import com.liushiqi.blogmain.vo.CategoryVo;

public interface CategoryService {

    CategoryVo createCategory(CategoryRequest req);
}
