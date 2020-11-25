package com.laisen.autojob.everphoto.controller;

import com.laisen.autojob.core.controller.BaseController;
import com.laisen.autojob.everphoto.dto.EverPhotoJobDTO;
import com.laisen.autojob.everphoto.entity.EverPhotoAccount;
import com.laisen.autojob.everphoto.repository.EverPhotoAccountRepository;
import com.laisen.autojob.quartz.EverPhotoJob;
import com.laisen.autojob.quartz.entity.QuartzBean;
import com.laisen.autojob.quartz.repository.QuartzBeanRepository;
import com.laisen.autojob.quartz.util.QuartzUtils;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

@RestController
@RequestMapping("/everphoto/")
public class EverphotoController extends BaseController {
    @Autowired
    private Scheduler scheduler;
    @Autowired
    private QuartzBeanRepository quartzBeanRepository;
    @Autowired
    private EverPhotoAccountRepository everPhotoAccountRepository;

    @PostMapping("/create")
    @ResponseBody
    public String createJob(HttpServletRequest request, EverPhotoJobDTO dto) {
        try {
            final Long userId = super.getUserIdByRequest(request);
            if (Objects.isNull(userId)) {
                return "用户信息不正确";
            }
            EverPhotoAccount everPhotoAccount = everPhotoAccountRepository.findByUserId(userId);
            if (!Objects.isNull(everPhotoAccount)) {
                if (!everPhotoAccount.getUserId().equals(userId)) {
                    return "此账号已被其他用户绑定";
                }
            } else {
                everPhotoAccount = new EverPhotoAccount();
            }
            everPhotoAccount.setUserId(userId);
            if (!dto.getAccount().startsWith("+86")) {
                everPhotoAccount.setAccount("+86" + dto.getAccount());
            } else {
                everPhotoAccount.setAccount(dto.getAccount());
            }
            everPhotoAccount.setPassword(DigestUtils.md5DigestAsHex(("tc.everphoto." + dto.getPassword()).getBytes()));

            everPhotoAccountRepository.save(everPhotoAccount);

            QuartzBean quartzBean = quartzBeanRepository.findByUserId(userId);
            if (Objects.isNull(quartzBean)) {
                quartzBean = new QuartzBean();
            }
            quartzBean.setUserId(userId);
            quartzBean.setJobClass(EverPhotoJob.class.getName());
            quartzBean.setJobName("everphoto.job." + userId);
            //"0 0 12 * * ?" 每天中午12点触发
            quartzBean.setCronExpression("0 0/20 " + dto.getTimeHour() + " * * ?");
//            quartzBean.setCronExpression("*/10 * * * * ?");

            quartzBeanRepository.save(quartzBean);

            QuartzUtils.createScheduleJob(scheduler, quartzBean);
            QuartzUtils.runOnce(scheduler, quartzBean.getJobName());
        } catch (Exception e) {
            e.printStackTrace();
            return "配置失败";
        }
        return "配置成功";
    }

    @PostMapping("/delete")
    @ResponseBody
    public String deleteJob(Long userId) {
        try {
            QuartzBean quartzBean = quartzBeanRepository.findByUserId(userId);
            if (!Objects.isNull(quartzBean)) {
                QuartzUtils.deleteScheduleJob(scheduler, quartzBean.getJobName());
                quartzBeanRepository.delete(quartzBean);
            }
            final EverPhotoAccount everPhotoAccount = everPhotoAccountRepository.findByUserId(userId);
            if (!Objects.isNull(everPhotoAccount)) {
                everPhotoAccountRepository.delete(everPhotoAccount);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "删除失败";
        }
        return "删除成功";
    }

}