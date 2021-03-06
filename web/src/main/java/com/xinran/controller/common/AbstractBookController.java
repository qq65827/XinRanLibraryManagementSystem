package com.xinran.controller.common;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.xinran.constant.BookType;
import com.xinran.constant.BorrowStatus;
import com.xinran.constant.ExceptionCode;
import com.xinran.controller.util.UserIdenetityUtil;
import com.xinran.event.Event;
import com.xinran.event.impl.BookOnStockEvent;
import com.xinran.event.util.EventListenerSupport;
import com.xinran.exception.BorrowOrReturnValidationException;
import com.xinran.exception.StockException;
import com.xinran.pojo.Book;
import com.xinran.pojo.BookLocation;
import com.xinran.pojo.BorrowReturnRecord;
import com.xinran.pojo.OnOffStockRecord;
import com.xinran.pojo.Pagination;
import com.xinran.pojo.User;
import com.xinran.service.BookLocationService;
import com.xinran.service.BookService;
import com.xinran.service.BorrowReturnRecordService;
import com.xinran.service.OnOffStockRecordService;
import com.xinran.service.UserService;
import com.xinran.util.DateUtil;
import com.xinran.util.ThreadLocalUtil;
import com.xinran.vo.AjaxResult;
import com.xinran.vo.BasicUserVO;
import com.xinran.vo.BookDetail;
import com.xinran.vo.BookLocationVO;
import com.xinran.vo.builder.AjaxResultBuilder;

/**
 * @author zhuangyao.zy
 */
public class AbstractBookController {

//    @Autowired
//    protected this      this;

    @Autowired
    protected BookService               bookService;

    @Autowired
    protected UserService               userService;

    @Autowired
    protected BookLocationService       bookLocationService;

    @Autowired
    protected OnOffStockRecordService   onOffStockRecordService;

    @Autowired
    protected BorrowReturnRecordService borrowReturnRecordService;

    @RequestMapping("/book/isbn/{isbn}")
    public @ResponseBody AjaxResult getBookByISBN(@PathVariable(value = "isbn") String isbn, HttpServletRequest request) {
        Book book = bookService.findBookByISBN(isbn);
        return AjaxResultBuilder.buildSuccessfulResult(book);
    }

    @RequestMapping("/book/donate/{bookId}")
    public @ResponseBody AjaxResult donate(@PathVariable(value = "bookId") Long bookId,
                                           @RequestParam("location") Long location,
                                           @RequestParam(value = "phone", required = false) String phone,
                                           HttpServletRequest request) {
        OnOffStockRecord onStock = null;
        try {
            onStock = this.onStock(bookId, location, phone, request, BookType.DONATED);
        } catch (StockException e) {
            return AjaxResultBuilder.buildFailedResult(400, e.getCode());
        }
        return AjaxResultBuilder.buildSuccessfulResult(onStock);
    }

    @RequestMapping("/book/share/{bookId}")
    public @ResponseBody AjaxResult share(@PathVariable(value = "bookId") Long bookId,
                                          @RequestParam("location") Long location,
                                          HttpServletRequest request) {
        OnOffStockRecord onStock = null;
        try {
            onStock = this.onStock(bookId, location, null, request, BookType.SHARED);
        } catch (StockException e) {
            return AjaxResultBuilder.buildFailedResult(400, e.getCode());
        }
        return AjaxResultBuilder.buildSuccessfulResult(onStock);
    }

    @RequestMapping("/book/offstock/{onStockId}")
    public
    @ResponseBody
    AjaxResult offStock(@PathVariable(value = "onStockId") Long onStockId, HttpServletRequest request) {
        OnOffStockRecord record = new OnOffStockRecord();
        record.setId(onStockId);
        try {
            onOffStockRecordService.offStock(record);
        } catch (StockException e) {
            return AjaxResultBuilder.buildFailedResult(400, e.getCode());
        }
        return AjaxResultBuilder.buildSuccessfulResult(null);
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
                throw new BorrowOrReturnValidationException(ExceptionCode.TheBookHasBeenBorrowed.getCode());
            }

            Long currentUserId = UserIdenetityUtil.getCurrentUserId(request);
            this.borrowBook(onOffStockRecord, currentUserId);

        }

        return AjaxResultBuilder.buildSuccessfulResult(null);
    }



    @RequestMapping("/book/return/{onStockId}")
    public @ResponseBody AjaxResult returnBook(@PathVariable(value = "onStockId") Long onStockId,
                                               HttpServletRequest request) throws BorrowOrReturnValidationException {
        Long currentUserId = UserIdenetityUtil.getCurrentUserId(request);
        try {
            this.returnBook(onStockId, currentUserId);
        } catch (BorrowOrReturnValidationException e) {
            return AjaxResultBuilder.buildFailedResult(400, e.getCode());
        }

        return AjaxResultBuilder.buildSuccessfulResult(null);
    }



    @RequestMapping("/book/donate/records")
    public @ResponseBody AjaxResult getDonatedRecords(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                      @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                      HttpServletRequest request) {
        Long userId = UserIdenetityUtil.getCurrentUserId(request);
        List<OnOffStockRecord> records = null;
        if (userId != null) {
            Pagination page = new Pagination();
            if (pageNo != null && pageNo >= 0) {
                page.setCurrent(pageNo);
            }
            if (pageSize != null && pageSize > 0) {
                page.setSize(pageSize);
            }
            records = onOffStockRecordService.findDonated(userId, page);
            List<BookDetail> bookDetailList = fillBookInfo(records);
            return AjaxResultBuilder.buildSuccessfulResult(bookDetailList);
        }
        return null;
    }

    @RequestMapping("/book/share/records")
    public @ResponseBody AjaxResult getSharedRecords(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                     @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                     HttpServletRequest request) {
        Long userId = UserIdenetityUtil.getCurrentUserId(request);
        List<OnOffStockRecord> records = null;
        if (userId != null) {
            Pagination page = new Pagination();
            if (pageNo != null && pageNo >= 0) {
                page.setCurrent(pageNo);
            }
            if (pageSize != null && pageSize > 0) {
                page.setSize(pageSize);
            }
            records = onOffStockRecordService.findShared(userId, page);
            List<BookDetail> bookDetailList = fillBookInfo(records);
            return AjaxResultBuilder.buildSuccessfulResult(bookDetailList);

        }
        return null;
    }

    @RequestMapping("/book/borrow/records")
    public @ResponseBody AjaxResult getBorrowedRecords(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                     @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                     HttpServletRequest request) {
        Long userId = UserIdenetityUtil.getCurrentUserId(request);
        List<BookDetail> bookDetailList = new ArrayList<BookDetail>();
        if (userId != null) {
            Pagination page = new Pagination();
            if (pageNo != null && pageNo >= 0) {
                page.setCurrent(pageNo);
            }
            if (pageSize != null && pageSize > 0) {
                page.setSize(pageSize);
            }
            List<BorrowReturnRecord> records = borrowReturnRecordService.findBorrowedBooks(userId, page);
            if (CollectionUtils.isNotEmpty(records)) {
                for (BorrowReturnRecord borrowReturnRecord : records) {
                    BookDetail bookDetail = this.buildBookDetail(borrowReturnRecord.getOnOffStockId());
                    bookDetailList.add(bookDetail);
                }
            }
        }
        return AjaxResultBuilder.buildSuccessfulResult(bookDetailList);
    }

    @RequestMapping("/book/return/records")
    public @ResponseBody AjaxResult getReturnedRecords(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                       @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                       HttpServletRequest request) {
        Long userId = UserIdenetityUtil.getCurrentUserId(request);
        List<BookDetail> bookDetailList = new ArrayList<BookDetail>();
        if (userId != null) {
            Pagination page = new Pagination();
            if (pageNo != null && pageNo >= 0) {
                page.setCurrent(pageNo);
            }
            if (pageSize != null && pageSize > 0) {
                page.setSize(pageSize);
            }
            List<BorrowReturnRecord> records = borrowReturnRecordService.findReturnedBooks(userId, page);
            if (CollectionUtils.isNotEmpty(records)) {
                for (BorrowReturnRecord borrowReturnRecord : records) {
                    BookDetail bookDetail = this.buildBookDetail(borrowReturnRecord.getOnOffStockId());
                    bookDetailList.add(bookDetail);
                }
            }
        }
        return AjaxResultBuilder.buildSuccessfulResult(bookDetailList);
    }


    private List<BookDetail> fillBookInfo(List<OnOffStockRecord> records) {

        List<BookDetail> bookDetailList = new ArrayList<>();
        for (OnOffStockRecord record : records) {
            // TODO 使用缓存以避免每次查询数据库。以ID查询书本信息在很多场景会使用。
            BookDetail bookDetail = this.buildBookDetail(record.getId());
            bookDetailList.add(bookDetail);
        }
        return bookDetailList;
    }

    //
    // @Autowired
    // protected BookService bookService;
    //
    // @Autowired
    // protected UserService userService;
    //
    // @Autowired
    // protected BookLocationService bookLocationService;
    //
    // @Autowired
    // protected OnOffStockRecordService onOffStockRecordService;
    //
    // @Autowired
    // protected BorrowReturnRecordService borrowReturnRecordService;

    public OnOffStockRecord onStock(Long bookId, Long location, String phone, HttpServletRequest request,
                                    BookType bookType) throws StockException {
        OnOffStockRecord record = new OnOffStockRecord();
        record.setOwnerUserId(UserIdenetityUtil.getCurrentUserId(request));
        record.setOwnerPhone(phone);
        record.setBookType(bookType.getType());
        record.setLocation(location);
        record.setBookId(bookId);
        record.setBorrowStatus(BorrowStatus.UNBORROWED.getStatus());
        record = onOffStockRecordService.onStock(record);

        try {
            ThreadLocalUtil.set(record);
            Event event = new BookOnStockEvent();
            EventListenerSupport.fireEvent(event);
        } finally {
            ThreadLocalUtil.remove(record);
        }

        return record;

    }

    public void borrowBook(OnOffStockRecord onOffStockRecord, Long currentUserId) {
        BorrowReturnRecord borrowReturnRecord = new BorrowReturnRecord();
        borrowReturnRecord.setBookId(onOffStockRecord.getBookId());
        borrowReturnRecord.setBookType(onOffStockRecord.getBookType());
        borrowReturnRecord.setBorrowStatus(BorrowStatus.BORROWED.getStatus());
        borrowReturnRecord.setBorrowUserId(currentUserId);
        borrowReturnRecord.setBorrowDate(DateUtil.getCurrentDate());
        borrowReturnRecord.setOnOffStockId(onOffStockRecord.getId());
        borrowReturnRecord.setOwnerUserId(onOffStockRecord.getOwnerUserId());
        borrowReturnRecordService.insert(borrowReturnRecord);

        onOffStockRecord.setBorrowId(borrowReturnRecord.getId());
        onOffStockRecord.setBorrowUserId(currentUserId);
        onOffStockRecord.setBorrowStatus(BorrowStatus.BORROWED.getStatus());
        onOffStockRecordService.updateOnOffStockRecord(onOffStockRecord);
    }

    public void returnBook(Long onStockId, Long currentUserId) throws BorrowOrReturnValidationException {
        OnOffStockRecord onOffStockRecord = onOffStockRecordService.findOnOffStockRecordById(onStockId);

        if (null == onOffStockRecord) {
            throw new BorrowOrReturnValidationException(ExceptionCode.InvalidOnOffStockId.getCode());
        } else {

            Integer borrowStatus = onOffStockRecord.getBorrowStatus();
            if (BorrowStatus.BORROWED.getStatus() != borrowStatus) {
                throw new BorrowOrReturnValidationException(ExceptionCode.TheBookShouldBeInBorrowedStatus.getCode());

            }

            if (!onOffStockRecord.getBorrowUserId().equals(currentUserId)) {
                throw new BorrowOrReturnValidationException(
                                                            ExceptionCode.TheBookYouReturnedShouldBeBorrowedByYou.getCode());

            }

            BorrowReturnRecord borrowReturnRecord = borrowReturnRecordService.findBorrowReturnRecordById(onOffStockRecord.getBorrowId());
            borrowReturnRecord.setBorrowStatus(BorrowStatus.UNBORROWED.getStatus());
            borrowReturnRecord.setReturnDate(DateUtil.getCurrentDate());

            borrowReturnRecordService.updateBorrowReturnRecord(borrowReturnRecord);

            onOffStockRecord.setBorrowStatus(BorrowStatus.UNBORROWED.getStatus());
            onOffStockRecord.setBorrowId(null);
            onOffStockRecord.setBorrowUserId(null);
            onOffStockRecordService.updateOnOffStockRecord(onOffStockRecord);
        }
    }

    public BookDetail buildBookDetail(Long onOffStockId) {

        OnOffStockRecord onOffStockRecord = onOffStockRecordService.findOnOffStockRecordById(onOffStockId);
        
        if(onOffStockRecord ==null){
            return new BookDetail();
        }
        
        Book book = bookService.findBookById(onOffStockRecord.getBookId());

        Long bookLocationId = onOffStockRecord.getLocation();
        BookLocation bookLocation = bookLocationService.findBookLocationById(bookLocationId);
        BookLocationVO bookLocationVO = new BookLocationVO();
        if (null != bookLocation) {
            bookLocationVO.setProvince(AbstractBookLocationController.getMap().get(bookLocation.getProvince()));
            bookLocationVO.setCity(AbstractBookLocationController.getMap().get(bookLocation.getCity()));
            bookLocationVO.setCounty(AbstractBookLocationController.getMap().get(bookLocation.getCity()));
            bookLocationVO.setDetail(bookLocation.getDetail());
        }

        Long ownerUserId = onOffStockRecord.getOwnerUserId();
        BasicUserVO basicUserVO = buildBasicUserVO(ownerUserId);

        Pagination pagination = new Pagination();
        List<BorrowReturnRecord> borrowReturnRecordList = borrowReturnRecordService.findHistroicBorrowedBooks(onOffStockRecord.getId(),
                                                                                                              pagination);
        List<BasicUserVO> basicUserVOList = null;
        if (CollectionUtils.isNotEmpty(borrowReturnRecordList)) {
            basicUserVOList = new ArrayList<BasicUserVO>();
            for (BorrowReturnRecord borrowReturnRecord : borrowReturnRecordList) {
                Long borrowUserId = borrowReturnRecord.getBorrowUserId();
                BasicUserVO basicUserVO1 = buildBasicUserVO(borrowUserId);
                basicUserVOList.add(basicUserVO1);
            }
        }

        BookDetail bookDetail = new BookDetail();
        bookDetail.setBook(book);
        bookDetail.setOnOffStockRecord(onOffStockRecord);
        bookDetail.setOwnerUserVO(basicUserVO);
        bookDetail.setBookLocationVO(bookLocationVO);
        bookDetail.setHistroicBorrowedRecordList(basicUserVOList);
        return bookDetail;
    }

    private BasicUserVO buildBasicUserVO(Long ownerUserId) {
        User user = userService.findUserByUserId(ownerUserId);
        BasicUserVO basicUserVO = new BasicUserVO();
        if (null != user) {
            BeanUtils.copyProperties(user, basicUserVO);
        }
        return basicUserVO;
    }


}
