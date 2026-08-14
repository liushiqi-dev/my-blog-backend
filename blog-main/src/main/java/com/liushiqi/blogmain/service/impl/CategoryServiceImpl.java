package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.dto.request.CategoryRequest;
import com.liushiqi.blogmain.mapper.CategoryMapper;
import com.liushiqi.blogmain.service.CategoryService;
import com.liushiqi.blogmain.vo.CategoryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     *不需要校验分类名称，因为数据库中已经将分类名称作为唯一键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryRequest createCategory(CategoryRequest req) {
        try {
            categoryMapper.insert(req);
        } catch (Exception e) {
            throw new BusinessException("分类名称已存在");
        }
        return req;
    }

    @Override
    public List<CategoryVo> listCategories(){
        return categoryMapper.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryRequest updateCategory(CategoryRequest req) {
        try {
            if (categoryMapper.update(req) == 0) {
                throw new BusinessException("分类不存在");
            }
        } catch (Exception e) {
            throw new BusinessException("分类名称已存在");
        }
        return req;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        if (categoryMapper.countPostsByCategory(id)!=null) {
            throw new BusinessException("分类下有文章，不能删除");
        }
        try {
            if (categoryMapper.delete(id) == 0) {
                throw new BusinessException("分类不存在");
            }
        } catch (Exception e) {
            throw new BusinessException("删除分类失败");
        }
    }
}
