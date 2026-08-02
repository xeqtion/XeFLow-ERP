package com.erp.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.task.entity.PersonalTask;
import com.erp.task.mapper.PersonalTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 个人待办服务 - 所有操作均限定在当前登录用户范围内。
 */
public interface PersonalTaskService extends IService<PersonalTask> {

    List<PersonalTask> listInRange(LocalDate start, LocalDate end);

    List<PersonalTask> listPending();

    Map<String, Object> stats();

    Long createTask(PersonalTask task);

    void updateTask(PersonalTask task);

    void toggle(Long id);

    void deleteTask(Long id);

    void upsertSystemTask(Long ownerId, String title, String content, LocalDate taskDate,
                          String priority, String category, String sourceType, Long sourceId, String sourceNo);

    void removeSystemTask(String sourceType, Long sourceId);

    @Service
    class Impl extends ServiceImpl<PersonalTaskMapper, PersonalTask> implements PersonalTaskService {

        @Override
        public List<PersonalTask> listInRange(LocalDate start, LocalDate end) {
            return list(base()
                    .ge(start != null, PersonalTask::getTaskDate, start)
                    .le(end != null, PersonalTask::getTaskDate, end)
                    .orderByAsc(PersonalTask::getTaskDate)
                    .orderByDesc(PersonalTask::getAllDay)
                    .orderByAsc(PersonalTask::getStartTime)
                    .orderByDesc(PersonalTask::getPriority));
        }

        @Override
        public List<PersonalTask> listPending() {
            return list(base()
                    .eq(PersonalTask::getStatus, "PENDING")
                    .orderByAsc(PersonalTask::getTaskDate)
                    .orderByDesc(PersonalTask::getAllDay)
                    .orderByAsc(PersonalTask::getStartTime));
        }

        @Override
        public Map<String, Object> stats() {
            LocalDate today = LocalDate.now();
            long pending = count(base().eq(PersonalTask::getStatus, "PENDING"));
            long todayCount = count(base().eq(PersonalTask::getStatus, "PENDING")
                    .eq(PersonalTask::getTaskDate, today));
            long overdue = count(base().eq(PersonalTask::getStatus, "PENDING")
                    .lt(PersonalTask::getTaskDate, today));
            return Map.of("pending", pending, "today", todayCount, "overdue", overdue);
        }

        @Override
        public Long createTask(PersonalTask task) {
            if (!StringUtils.hasText(task.getTitle())) {
                throw new BusinessException("标题不能为空");
            }
            task.setId(null);
            task.setOwnerId(currentUserId());
            if (task.getTaskDate() == null) {
                task.setTaskDate(LocalDate.now());
            }
            if (!StringUtils.hasText(task.getPriority())) {
                task.setPriority("NORMAL");
            }
            if (!StringUtils.hasText(task.getCategory())) {
                task.setCategory("MANUAL");
            }
            normalizeSchedule(task);
            task.setSystemGenerated(0);
            task.setSourceType(null);
            task.setSourceId(null);
            task.setSourceNo(null);
            task.setStatus("PENDING");
            task.setDoneTime(null);
            save(task);
            return task.getId();
        }

        @Override
        public void updateTask(PersonalTask task) {
            PersonalTask db = mine(task.getId());
            db.setTitle(task.getTitle());
            db.setContent(task.getContent());
            db.setTaskDate(task.getTaskDate());
            db.setAllDay(task.getAllDay());
            db.setStartTime(task.getStartTime());
            db.setEndTime(task.getEndTime());
            db.setLocation(task.getLocation());
            db.setPriority(task.getPriority());
            normalizeSchedule(db);
            updateById(db);
        }

        @Override
        public void toggle(Long id) {
            PersonalTask db = mine(id);
            if ("DONE".equals(db.getStatus())) {
                db.setStatus("PENDING");
                db.setDoneTime(null);
            } else {
                db.setStatus("DONE");
                db.setDoneTime(LocalDateTime.now());
            }
            updateById(db);
        }

        @Override
        public void deleteTask(Long id) {
            mine(id);
            removeById(id);
        }

        @Override
        public void upsertSystemTask(Long ownerId, String title, String content, LocalDate taskDate,
                                     String priority, String category, String sourceType, Long sourceId, String sourceNo) {
            if (ownerId == null || sourceId == null || !StringUtils.hasText(sourceType)) {
                return;
            }
            if (taskDate == null) {
                removeSystemTask(sourceType, sourceId);
                return;
            }
            PersonalTask task = getOne(new LambdaQueryWrapper<PersonalTask>()
                    .eq(PersonalTask::getOwnerId, ownerId)
                    .eq(PersonalTask::getSourceType, sourceType)
                    .eq(PersonalTask::getSourceId, sourceId)
                    .eq(PersonalTask::getSystemGenerated, 1)
                    .last("LIMIT 1"));
            if (task == null) {
                task = new PersonalTask();
                task.setOwnerId(ownerId);
                task.setSourceType(sourceType);
                task.setSourceId(sourceId);
                task.setSystemGenerated(1);
            }
            task.setTitle(StringUtils.hasText(title) ? title.trim() : "系统事项");
            task.setContent(content);
            task.setTaskDate(taskDate);
            task.setAllDay(1);
            task.setStartTime(null);
            task.setEndTime(null);
            task.setLocation(null);
            task.setPriority(StringUtils.hasText(priority) ? priority : "NORMAL");
            task.setCategory(StringUtils.hasText(category) ? category : sourceType);
            task.setSourceNo(sourceNo);
            task.setStatus("PENDING");
            task.setDoneTime(null);
            saveOrUpdate(task);
        }

        @Override
        public void removeSystemTask(String sourceType, Long sourceId) {
            if (!StringUtils.hasText(sourceType) || sourceId == null) {
                return;
            }
            remove(new LambdaQueryWrapper<PersonalTask>()
                    .eq(PersonalTask::getSourceType, sourceType)
                    .eq(PersonalTask::getSourceId, sourceId)
                    .eq(PersonalTask::getSystemGenerated, 1));
        }

        /** 限定当前用户的查询条件 */
        private LambdaQueryWrapper<PersonalTask> base() {
            return new LambdaQueryWrapper<PersonalTask>().eq(PersonalTask::getOwnerId, currentUserId());
        }

        /** 取出属于当前用户的任务, 否则拒绝 */
        private PersonalTask mine(Long id) {
            if (id == null) {
                throw new BusinessException("缺少 id");
            }
            PersonalTask db = getById(id);
            if (db == null || !currentUserId().equals(db.getOwnerId())) {
                throw new BusinessException("待办不存在");
            }
            return db;
        }

        private void normalizeSchedule(PersonalTask task) {
            if (task.getAllDay() == null) {
                task.setAllDay((task.getStartTime() == null && task.getEndTime() == null) ? 1 : 0);
            }
            if (task.getAllDay() != null && task.getAllDay() == 1) {
                task.setStartTime(null);
                task.setEndTime(null);
                return;
            }
            LocalTime start = task.getStartTime();
            LocalTime end = task.getEndTime();
            if (start == null && end != null) {
                throw new BusinessException("请填写开始时间");
            }
            if (start != null && end == null) {
                task.setEndTime(start.plusHours(1));
                end = task.getEndTime();
            }
            if (start != null && end != null && !end.isAfter(start)) {
                throw new BusinessException("结束时间必须晚于开始时间");
            }
            task.setAllDay(0);
        }

        private Long currentUserId() {
            Long uid = SecurityUtils.getCurrentUserId();
            if (uid == null) {
                throw new BusinessException("未登录");
            }
            return uid;
        }
    }
}
