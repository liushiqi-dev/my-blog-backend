package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.request.CategoryRequest;
import com.liushiqi.blogmain.service.CategoryService;
import com.liushiqi.blogmain.vo.CategoryVo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result createCategory(@Valid @RequestBody CategoryRequest req) {
        CategoryVo categoryVo = categoryService.createCategory(req);
        log.info("创建分类成功，分类ID：{}，分类名称：{}", categoryVo.getId(),categoryVo.getName());
        return Result.success(categoryVo);
    }
}
