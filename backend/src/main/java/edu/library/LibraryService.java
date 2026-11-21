package edu.library;

import edu.library.LibraryController.BookInput;
import edu.library.LibraryController.FeedbackInput;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class LibraryService {
    enum LoanStatus { PENDING, APPROVED, REJECTED, RETURNED }

    private static final String LOAN_COLUMNS = """
            SELECT l.id, l.book_id AS bookId, b.title, l.status,
              DATE_FORMAT(l.requested_at, '%Y-%m-%d %H:%i:%s') AS requestedAt,
              DATE_FORMAT(l.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt
            """;
    private static final String LOAN_JOIN = " FROM loans l JOIN books b ON b.id=l.book_id ";
    private final JdbcTemplate db;

    LibraryService(JdbcTemplate db) {
        this.db = db;
    }

    List<Map<String, Object>> books() {
        return db.queryForList("""
                SELECT id,title,author,category,description,total,borrowed,total-borrowed AS available
                FROM books WHERE deleted=FALSE ORDER BY id DESC
                """);
    }

    Map<String, Object> stats() {
        return db.queryForMap("""
                SELECT inventory.bookTitles, inventory.totalCopies, inventory.borrowedCopies,
                       inventory.totalCopies - inventory.borrowedCopies AS availableCopies,
                       (SELECT COUNT(*) FROM users WHERE role='READER') AS readers,
                       activity.activeReaders, activity.pendingLoans
                FROM (
                    SELECT COUNT(*) AS bookTitles, COALESCE(SUM(total), 0) AS totalCopies,
                           COALESCE(SUM(borrowed), 0) AS borrowedCopies
                    FROM books WHERE deleted=FALSE
                ) inventory
                CROSS JOIN (
                    SELECT COUNT(DISTINCT CASE WHEN status='APPROVED' THEN user_id END) AS activeReaders,
                           COUNT(CASE WHEN status='PENDING' THEN 1 END) AS pendingLoans
                    FROM loans WHERE status IN ('APPROVED', 'PENDING')
                ) activity
                """);
    }

    void submitFeedback(long readerId, FeedbackInput input) {
        db.update("INSERT INTO feedback(user_id, subject, message) VALUES (?, ?, ?)",
                readerId, input.subject().strip(), input.message().strip());
    }

    List<Map<String, Object>> feedback() {
        return db.queryForList("""
                SELECT f.id, u.username, f.subject, f.message,
                       DATE_FORMAT(f.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(f.resolved_at, '%Y-%m-%d %H:%i:%s') AS resolvedAt
                FROM feedback f JOIN users u ON u.id=f.user_id
                ORDER BY f.resolved_at IS NOT NULL, f.id DESC
                """);
    }

    void resolveFeedback(long id) {
        db.update("UPDATE feedback SET resolved_at=CURRENT_TIMESTAMP WHERE id=? AND resolved_at IS NULL", id);
        if (db.queryForObject("SELECT COUNT(*) FROM feedback WHERE id=?", Integer.class, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在");
        }
    }

    List<Map<String, Object>> publicLoans() {
        return db.queryForList(LOAN_COLUMNS + LOAN_JOIN + "ORDER BY l.id DESC");
    }

    List<Map<String, Object>> readerLoans(long userId) {
        return db.queryForList(LOAN_COLUMNS + LOAN_JOIN + "WHERE l.user_id=? ORDER BY l.id DESC", userId);
    }

    List<Map<String, Object>> adminLoans() {
        return db.queryForList(LOAN_COLUMNS + ", u.username " + LOAN_JOIN
                + "JOIN users u ON u.id=l.user_id ORDER BY l.id DESC");
    }

    void createBook(BookInput book) {
        db.update("INSERT INTO books(title,author,category,description,total) VALUES (?,?,?,?,?)",
                book.title().strip(), book.author().strip(), book.category().strip(),
                description(book), book.total());
    }

    @Transactional
    public void updateBook(long id, BookInput book) {
        var current = lockBook(id);
        if (book.total() < current.borrowed()) {
            throw conflict("馆藏数量不能少于已借出数量");
        }
        db.update("UPDATE books SET title=?,author=?,category=?,description=?,total=? WHERE id=?",
                book.title().strip(), book.author().strip(), book.category().strip(),
                description(book), book.total(), id);
    }

    @Transactional
    public void deleteBook(long id) {
        lockBook(id);
        int active = db.queryForObject("""
                SELECT COUNT(*) FROM loans WHERE book_id=? AND status IN ('PENDING','APPROVED')
                """, Integer.class, id);
        if (active > 0) {
            throw conflict("请先处理待审批申请或登记归还");
        }
        db.update("UPDATE books SET deleted=TRUE WHERE id=?", id);
    }

    @Transactional
    public void borrow(long bookId, long userId) {
        var book = lockBook(bookId);
        if (book.borrowed() >= book.total()) {
            throw conflict("当前没有可借库存");
        }
        int active = db.queryForObject("""
                SELECT COUNT(*) FROM loans WHERE user_id=? AND book_id=? AND status IN ('PENDING','APPROVED')
                """, Integer.class, userId, bookId);
        if (active > 0) {
            throw conflict("该书已有待审批或借出的记录");
        }
        db.update("INSERT INTO loans(book_id,user_id) VALUES (?,?)", bookId, userId);
    }

    @Transactional
    public void processLoan(long id, LoanStatus next) {
        transitionLoan(id, next, null);
    }

    @Transactional
    public void returnLoan(long id, long userId) {
        transitionLoan(id, LoanStatus.RETURNED, userId);
    }

    private void transitionLoan(long id, LoanStatus next, Long userId) {
        var books = userId == null
                ? db.queryForList("SELECT book_id FROM loans WHERE id=?", Long.class, id)
                : db.queryForList("SELECT book_id FROM loans WHERE id=? AND user_id=?", Long.class, id, userId);
        if (books.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "借阅记录不存在");
        }
        long bookId = books.get(0);
        var book = lockBook(bookId);
        String current = db.queryForObject("SELECT status FROM loans WHERE id=? FOR UPDATE", String.class, id);
        if ("PENDING".equals(current) && next == LoanStatus.APPROVED) {
            if (book.borrowed() >= book.total()) {
                throw conflict("库存不足，无法批准");
            }
            db.update("UPDATE books SET borrowed=borrowed+1 WHERE id=?", bookId);
        } else if ("APPROVED".equals(current) && next == LoanStatus.RETURNED) {
            db.update("UPDATE books SET borrowed=borrowed-1 WHERE id=?", bookId);
        } else if (!("PENDING".equals(current) && next == LoanStatus.REJECTED)) {
            throw conflict("当前状态不允许此操作，请刷新后重试");
        }
        db.update("UPDATE loans SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", next.name(), id);
    }

    private record Stock(int total, int borrowed) {}

    private Stock lockBook(long id) {
        var rows = db.query("SELECT total,borrowed FROM books WHERE id=? AND deleted=FALSE FOR UPDATE",
                (row, index) -> new Stock(row.getInt("total"), row.getInt("borrowed")), id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图书不存在或已下架");
        }
        return rows.get(0);
    }

    private String description(BookInput book) {
        return book.description() == null ? "" : book.description().strip();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
