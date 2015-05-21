package com.xinran.pojo;

import java.util.Date;

import com.xinran.util.DateUtil;

import lombok.Data;

/**
 * 借还记录 Created by 高海军 帝奇 on 2015 Feb 21 16:45.
 */
@Data
public class BorrowReturnRecord {

    public BorrowReturnRecord() {
        Date now = DateUtil.getCurrentDate();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private Long    id;
    private Date    createdAt;
    private Date    updatedAt;


    private Long    borrowUserId;
    private Long    ownerUserId;
    private Long    onOffStockId;
    private Long    bookId;
    private int     bookType;

    private Date    borrowDate;
    private Date    returnDate;
    private Integer borrowStatus;

}
