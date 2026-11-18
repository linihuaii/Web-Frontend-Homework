package edu.library;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class LibraryController {
    record BookInput(
            @NotBlank(message = "请输入书名") @Size(max = 150, message = "书名最多 150 字") String title,
            @NotBlank(message = "请输入作者") @Size(max = 100, message = "作者最多 100 字") String author,
            @NotBlank(message = "请输入分类") @Size(max = 60, message = "分类最多 60 字") String category,
            @Size(max = 1000, message = "简介最多 1000 字") String description,
            @NotNull(message = "请输入馆藏数量") @Min(value = 0, message = "馆藏数量不能为负数") Integer total) {}
    record LoanInput(@NotNull(message = "请选择图书") @Positive Long bookId) {}

    private final LibraryService library;

    LibraryController(LibraryService library) {
        this.library = library;
    }

    @GetMapping("/books")
    List<Map<String, Object>> books() {
        return library.books();
    }

    @GetMapping("/loans")
    List<Map<String, Object>> loans(HttpServletRequest request) {
        AuthController.require(request, null);
        return library.publicLoans();
    }

    @GetMapping("/loans/mine")
    List<Map<String, Object>> mine(HttpServletRequest request) {
        return library.readerLoans(AuthController.require(request, "READER").id());
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    void borrow(@Valid @RequestBody LoanInput input, HttpServletRequest request) {
        library.borrow(input.bookId(), AuthController.require(request, "READER").id());
    }

    @PostMapping("/admin/books")
    @ResponseStatus(HttpStatus.CREATED)
    void createBook(@Valid @RequestBody BookInput input, HttpServletRequest request) {
        AuthController.require(request, "ADMIN");
        library.createBook(input);
    }

    @PutMapping("/admin/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateBook(@PathVariable long id, @Valid @RequestBody BookInput input, HttpServletRequest request) {
        AuthController.require(request, "ADMIN");
        library.updateBook(id, input);
    }

    @DeleteMapping("/admin/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBook(@PathVariable long id, HttpServletRequest request) {
        AuthController.require(request, "ADMIN");
        library.deleteBook(id);
    }

    @GetMapping("/admin/loans")
    List<Map<String, Object>> adminLoans(HttpServletRequest request) {
        AuthController.require(request, "ADMIN");
        return library.adminLoans();
    }

    @PatchMapping(value = "/admin/loans/{id}", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void processLoan(@PathVariable long id, @RequestBody LibraryService.LoanStatus status,
                     HttpServletRequest request) {
        AuthController.require(request, "ADMIN");
        library.processLoan(id, status);
    }
}
