package com.xinran.dao.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.xinran.pojo.Activity;
import com.xinran.pojo.Pagination;

/**
 * @author 高海军 帝奇 Jun 3, 2015 9:44:35 PM
 */
public interface ActivityMapper {

    @Select("SELECT * FROM activity WHERE id = #{id}")
    public Activity findById(@Param("id") Long id);

    @Select("SELECT * FROM activity order by id desc limit #{page.start},#{page.end}")
    List<Activity> findActivities(@Param("page") Pagination page);

    @Insert("insert into activity (created_At, updated_At,title, memo,start_date,end_date,type,action,score)  values ( now(),now(),#{title}, #{memo},"
            + " #{startDate}, #{endDate}, #{type}, #{action}, #{score} )")
    // @Options(useGeneratedKeys = true, keyProperty = "id")
    @Options(useGeneratedKeys = true)
    public void addActivity(Activity activity);

    @Update("update  activity set   updated_At = #{updatedAt},title =  #{title}, memo = #{memo},start_date=#{startDate},"
            + " end_date=#{endDate},type=#{type}, action= #{action},score =#{score} where id = #{id}")
    public int updateActivity(Activity activity);

}
