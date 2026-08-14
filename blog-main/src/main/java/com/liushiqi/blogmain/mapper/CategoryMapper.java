package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.dto.request.CategoryRequest;
import com.liushiqi.blogmain.vo.CategoryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CategoryMapper {

    void insert(CategoryRequest req);

    @Select("select c.name , count(p.category_id) total from categories c left join post_categories p on" +
            "            p.category_id=c.id group by c.id;")
    List<CategoryVo> findAll();

    @Update("update categories set name=#{name} where id=#{id}")
    int update(CategoryRequest req);
}
