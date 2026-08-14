package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.dto.request.CategoryRequest;
import com.liushiqi.blogmain.vo.CategoryVo;
import java.util.List;

public interface CategoryService {

    CategoryRequest createCategory(CategoryRequest req);

    List<CategoryVo> listCategories();

    CategoryRequest updateCategory(CategoryRequest req);

    void deleteCategory(Long id);
}
