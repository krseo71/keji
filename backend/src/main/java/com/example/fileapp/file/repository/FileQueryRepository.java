package com.example.fileapp.file.repository;

import com.example.fileapp.file.domain.FileSource;
import com.example.fileapp.file.domain.QStoredFile;
import com.example.fileapp.file.domain.StoredFile;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FileQueryRepository {

    private final JPAQueryFactory query;

    public Page<StoredFile> search(Long ownerId, String extension, FileSource source, String keyword, Pageable pageable) {
        QStoredFile f = QStoredFile.storedFile;
        BooleanBuilder where = new BooleanBuilder();
        where.and(f.ownerId.eq(ownerId));
        if (extension != null && !extension.isBlank()) where.and(f.extension.eq(extension.toLowerCase()));
        if (source != null) where.and(f.source.eq(source));
        if (keyword != null && !keyword.isBlank()) {
            BooleanExpression name = f.originalName.containsIgnoreCase(keyword);
            BooleanExpression desc = f.description.containsIgnoreCase(keyword);
            where.and(name.or(desc));
        }

        List<StoredFile> content = query.selectFrom(f)
                .where(where)
                .orderBy(f.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = query.select(f.count()).from(f).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
