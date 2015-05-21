
package com.xinran.controller.mobile;


import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.xinran.constant.ApplicationConstant;
import com.xinran.constant.BookType;
import com.xinran.constant.BorrowStatus;
import com.xinran.constant.ExceptionCode;
import com.xinran.exception.BorrowOrReturnValidationException;
import com.xinran.pojo.Book;
import com.xinran.pojo.BorrowReturnRecord;
import com.xinran.pojo.OnOffStockRecord;
import com.xinran.pojo.Pagination;
import com.xinran.service.BookService;
import com.xinran.service.OnOffStockRecordService;
import com.xinran.util.DateUtil;
import com.xinran.vo.AjaxResult;
import com.xinran.vo.builder.AjaxResultBuilder;

/**
 * 
 * @author zhuangyao.zy
 *
 */
@RestController
@RequestMapping("/mobile")
public class BookController {


    @Autowired
    private BookService bookService;

    @Autowired
    private OnOffStockRecordService onOffStockRecordService;

    @RequestMapping("/book/isbn/{isbn}")
    public @ResponseBody AjaxResult getBookByISBN(@PathVariable(value = "isbn") String isbn, HttpServletRequest request) {
    	Book book = bookService.findBookByISBN(isbn);
    	return AjaxResultBuilder.buildSuccessfulResult(book);
    }
    
    @RequestMapping("/book/donate/{bookId}")
    public @ResponseBody AjaxResult donate(@PathVariable(value="bookId") Long bookId, 
                                           @RequestParam("location") Long location,
                                           @RequestParam("phone") String phone,
    		HttpServletRequest request){
    	OnOffStockRecord onStock = onStock(bookId,location,phone,request,BookType.DONATED);
        return AjaxResultBuilder.buildSuccessfulResult(onStock);
    }
    
    @RequestMapping("/book/share/{bookId}")
    public @ResponseBody AjaxResult share(@PathVariable(value="bookId") Long bookId, 
                                          @RequestParam("location") Long location, @RequestParam("phone") String phone,
    		HttpServletRequest request){
    	OnOffStockRecord onStock = onStock(bookId,location,phone,request,BookType.SHARED);
        return AjaxResultBuilder.buildSuccessfulResult(onStock);
    }
    
    private OnOffStockRecord onStock(Long bookId, Long location, String phone,
    		HttpServletRequest request, BookType bookType){
    	OnOffStockRecord record = new OnOffStockRecord();
        record.setOwnerUserId(getCurrentUserId(request));
    	record.setOwnerPhone(phone);
        record.setBookType(bookType.getType());
    	record.setLocation(location);
    	record.setBookId(bookId);
    	record.setBorrowStatus(BorrowStatus.UNBORROWED.getStatus());
        record = onOffStockRecordService.onStock(record);
    	return record;
    	
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        return (Long) request.getSession().getAttribute(ApplicationConstant.USER_ID);
    }

    @RequestMapping("/book/borrow/{onStockId}")
    public @ResponseBody AjaxResult borrow(@PathVariable(value = "onStockId") Long onStockId, HttpServletRequest request)
                                                                                                                         throws BorrowOrReturnValidationException {
        OnOffStockRecord onOffStockRecord = onOffStockRecordService.findOnOffStockRecordById(onStockId);
        if (null == onOffStockRecord) {
            throw new BorrowOrReturnValidationException(ExceptionCode.InvalidOnOffStockId.getCode());
        } else {
            Integer borrowStatus = onOffStockRecord.getBorrowStatus();
            if (BorrowStatus.UNBORROWED.getStatus() != borrowStatus) {
                throw new BorrowOrReturnValidationException(
                                                            ExceptionCode.TheOnOffStockIdLinkedBookHasBeenBorrowed.getCode());

            }

            BorrowReturnRecord borrowReturnRecord = new BorrowReturnRecord();
            borrowReturnRecord.setBookId(onOffStockRecord.getBookId());
            borrowReturnRecord.setBookType(onOffStockRecord.getBookType());
            borrowReturnRecord.setBorrowStatus(BorrowStatus.BORROWED.getStatus());
            borrowReturnRecord.setBorrowUserId(getCurrentUserId(request));
            borrowReturnRecord.setBorrowDate(DateUtil.getCurrentDate());
            borrowReturnRecord.setOnOffStockId(onOffStockRecord.getId());
            borrowReturnRecord.setOwnerUserId(onOffStockRecord.getOwnerUserId());

        }

        return AjaxResultBuilder.buildSuccessfulResult(null);
    }
    
    
    @RequestMapping("/book/donate/records")
    public @ResponseBody AjaxResult getDonatedRecords(@RequestParam(value = "pageNo", required=false) Integer pageNo,@RequestParam(value = "pageSize", required=false) Integer pageSize, HttpServletRequest request) {
    	Long userId = getCurrentUserId(request);
    	userId = 1L;
    	List<OnOffStockRecord> records = null;
    	if(userId != null){
    		Pagination page = new Pagination();
    		if(pageNo != null && pageNo >= 0){
    			page.setCurrent(pageNo);
    		}
    		if(pageSize != null && pageSize > 0){
    			page.setSize(pageSize);
    		}
    		records = onOffStockRecordService.findDonated(userId, page);
    		fillBookInfo(records);
    	}
    	return AjaxResultBuilder.buildSuccessfulResult(records);
    }
    
    @RequestMapping("/book/share/records")
    public @ResponseBody AjaxResult getSharedRecords(@RequestParam(value = "pageNo", required=false) Integer pageNo, @RequestParam(value = "pageSize", required=false) Integer pageSize, HttpServletRequest request) {
    	Long userId = getCurrentUserId(request);
    	userId = 1L;
    	List<OnOffStockRecord> records = null;
    	if(userId != null){
    		Pagination page = new Pagination();
    		if(pageNo != null && pageNo >= 0){
    			page.setCurrent(pageNo);
    		}
    		if(pageSize != null && pageSize > 0){
    			page.setSize(pageSize);
    		}
    		records = onOffStockRecordService.findShared(userId, page);
    		fillBookInfo(records);
    	}
    	return AjaxResultBuilder.buildSuccessfulResult(records);
    }
    
    
    @RequestMapping("/book/search/{keyword}")
    public @ResponseBody AjaxResult search(@PathVariable(value = "keyword") String keyword, HttpServletRequest request) {
    	List<Book> books = null;
    	Book book = bookService.findBookByISBN(keyword, true);
    	if(book != null){
    		books = new ArrayList<Book>();
    		books.add(book);
    	}else{
    		books = bookService.queryByTitle(keyword);
    	}
    	return AjaxResultBuilder.buildSuccessfulResult(books);
    }

	private void fillBookInfo(List<OnOffStockRecord> records) {
		if(records == null || records.size() < 1){
			return;
		}
		for(OnOffStockRecord record : records){
			// TODO 使用缓存以避免每次查询数据库。以ID查询书本信息在很多场景会使用。
			record.setBook(bookService.findBookById(record.getBookId()));
		}
	}

}
