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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result createCategory(@Valid @RequestBody CategoryRequest req) {
        req = categoryService.createCategory(req);
        log.info("创建分类成功，分类ID：{}，分类名称：{}", req.getId(),req.getName());
        return Result.success(req);
    }

    @GetMapping("/list")
    public Result listCategories(){
        List<CategoryVo> categoryVos = categoryService.listCategories();
        log.info("查询分类列表成功，分类数量：{}", categoryVos.size());
        return Result.success(categoryVos);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public Result updateCategory(@Valid @RequestBody CategoryRequest req){
        req = categoryService.updateCategory(req);
        log.info("更新分类成功，分类ID：{}，分类名称：{}", req.getId(),req.getName());
        return Result.success(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result deleteCategory(@PathVariable Long id){
        categoryService.deleteCategory(id);
        log.info("删除分类成功，分类ID：{}", id);
        return Result.success();
    }
}
