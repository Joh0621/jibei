package com.bonc.jibei.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bonc.jibei.api.Result;
import com.bonc.jibei.api.ResultCode;
import com.bonc.jibei.config.WordCfgProperties;
import com.bonc.jibei.entity.ReportAuthLog;
import com.bonc.jibei.entity.ReportMng;
import com.bonc.jibei.mapper.ReportAuthLogMapper;
import com.bonc.jibei.mapper.ReportMngMapper;
import com.bonc.jibei.mapper.ReportModelInterMapper;
import com.bonc.jibei.service.ReportAuthLogService;
import com.bonc.jibei.service.ReportMngService;
import com.bonc.jibei.util.FileDownloadUtil;
import com.bonc.jibei.vo.ReportMngList;
import com.bonc.jibei.vo.ReportMngPatchPub;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: dupengling
 * @DateTime: 2022/4/19 14:00s
 * @Description: TODO
 */
@Api(tags = "报告管理接口")
@RestController
public class ReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);

    @Resource
    private ReportMngService reportMngService;

    @Resource
    private ReportMngMapper reportMngMapper;

    @Resource
    private ReportAuthLogMapper reportAuthLogMapper;

    @Resource
    private ReportAuthLogService reportAuthLogService;

    @Resource
    private FileDownloadUtil fileDownloadUtil;

    @Resource
    private WordCfgProperties wordCfgProperties;

    @Resource
    private ReportModelInterMapper reportModelInterMapper;

    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "当前页，默认值为 1", required = true),
            @ApiImplicitParam(name = "size", value = "页大小，默认值为 10", required = true),
            @ApiImplicitParam(name = "stationName", value = "场站名称", required = false),
            @ApiImplicitParam(name = "year", value = "年份", required = false),
            @ApiImplicitParam(name = "quarter", value = "季度", required = false),
            @ApiImplicitParam(name = "stationType", value = "场站类型", required = false),
            @ApiImplicitParam(name = "reportStatus", value = "报告状态", required = false),
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportMngList.class),
    })
    @ApiOperation(value = "报告管理列表")
    @GetMapping("/report/list")
    public Result ReportMngList(@ApiIgnore Page<ReportMngList> page, String stationName, Integer year, Integer quarter, Integer stationType, Integer reportStatus) {
        Page<ReportMngList> jpage = new Page<>(page.getCurrent(), page.getSize());
        //long start = (page.getCurrent() - 1) * page.getSize();
        //long size = page.getSize();
        jpage.setSearchCount(false);
        List<ReportMngList> list = reportMngService.reportMngList(jpage, stationName, year, quarter, stationType, reportStatus);
        jpage.setRecords(list);
//        Integer cnt = reportMngMapper.selectCount(stationName, year, quarter, stationType, reportStatus);
        jpage.setTotal(reportMngMapper.selectCount(stationName, year, quarter, stationType, reportStatus));
        return Result.of(jpage);
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "报告id", required = true),
            @ApiImplicitParam(name = "reportStatus", value = "报告状态,0=没发布,1=发布.2:提交到队列,3：正在处理", required = true),
            @ApiImplicitParam(name = "memo", value = "审批意见", required = false),
    })
    @ApiOperation(value = "报告管理复核发布")
    @GetMapping("/report/publish")
    public Result updateReportStatus(Integer id, Integer reportStatus, String memo) {

        QueryWrapper<ReportMng> qw = new QueryWrapper<>();
        qw.eq("id", id);

        ReportMng reportMng = reportMngMapper.selectById(id);
        if (reportMng == null) {
            return Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
        }
        reportMng.setReportStatus(reportStatus);
        reportMngMapper.updateById(reportMng);
        reportAuthLogService.insertReportAuth(id, "张章", "复核发布", memo);
        return Result.ok();
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "报告id", required = true),
            @ApiImplicitParam(name = "reportStatus", value = "报告状态,0=没发布,1=发布.2:提交到队列,3：正在处理", required = true),
            @ApiImplicitParam(name = "memo", value = "审批意见", required = false),
    })
    @ApiOperation(value = "报告管理取消发布")
    @GetMapping("/report/pubcancell")
    public Result cancellReportStatus(Integer id, Integer reportStatus, String memo) {
        QueryWrapper<ReportMng> qw = new QueryWrapper<>();
        qw.eq("id", id);
        ReportMng ReportMng = reportMngMapper.selectById(id);
        if (ReportMng == null) {
            return Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
        }
        ReportMng.setReportStatus(reportStatus);
//        jbReportMng.setReportStatus(0);
        reportMngMapper.updateById(ReportMng);

        reportAuthLogService.insertReportAuth(id, "张章", "取消发布", memo);
        return Result.ok();
    }
    @ApiImplicitParams({
            @ApiImplicitParam(name = "idsList", value = "报告id", required = true),
            @ApiImplicitParam(name = "reportStatus", value = "报告状态,0=没发布,1=发布.2:提交到队列,3：正在处理", required = true),
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportMngList.class),
    })
    @ApiOperation(value = "批量复核")
    @PostMapping("/report/patchpublish")
    @ResponseBody
    public Result patchupdateReportStatus(@RequestBody(required = false) ReportMngPatchPub params) {
        List<String> msglist = null;
        Integer[] idsList = params.getIdsList();
        for (Integer id : idsList) {
            if (id == null || id <= 0) {
                continue;
            }
            // 记录已复核发布的场站
            reportAuthLogService.insertReportAuth(id, "张章", "复核发布", "");
            // 更新报告状态
            ReportMng mng = reportMngMapper.selectById(id);
            mng.setReportStatus(1);
            reportMngMapper.updateById(mng);
        }
        return Result.of(msglist);
    }

    @ApiOperation(value = "报告管理审批记录列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "报告id", required = true),
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportAuthLog.class),
    })
    @GetMapping("/report/loglist")
    public Result jbReportMngLogList(Integer id) {
        QueryWrapper<ReportAuthLog> qw = new QueryWrapper<>();
        qw.eq("report_id", id);
        List<ReportAuthLog> list = reportAuthLogMapper.selectList(qw);
        return Result.of(list);
    }

    @ApiOperation(value = "报告管理报告下载")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "idsList", value = "报告id", required = true)
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportAuthLog.class),
    })
    @GetMapping("/report/reportdownload")
    @ResponseBody
    @CrossOrigin
//    public Result reportDownload(@RequestBody(required = false) reportMngPatchPub params, HttpServletResponse response) {
    public void reportDownload(@RequestParam(required = false) Integer[] idsList, HttpServletResponse response) {
        // 获取所有url
        List<Integer> idList = Arrays.asList(idsList);
        List<String> fileSrcPaths = reportMngService.urlList(idList);
        // 生成压缩包下载
        response.addHeader("Access-Allow-Control-Origin","*");
        fileDownloadUtil.downloadZipFiles(response, fileSrcPaths);
//        QueryWrapper<ReportAuthLog> qw = new QueryWrapper<>();
//        qw.eq("report_id", id);
//        List<ReportAuthLog> list = reportAuthLogMapper.selectList(qw);
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "idsList", value = "报告id", required = true),
            @ApiImplicitParam(name = "reportStatus", value = "报告状态,0=没发布,1=发布.2:提交到队列(重新生成),3：正在处理 ", required = false),
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportMngList.class),
    })
    @ApiOperation(value = "重新生成(放到队列,批量生成用)")
    @PostMapping("/report/patchcreate")
    public Result patchUpdateStatus(@RequestParam(required = false) Integer[] idsList) {
        List<String> msglist = null;
        for (Integer id : idsList) {
            if (id == null || id <= 0) {
                continue;
            }
            // 更新报告状态
            ReportMng mng = reportMngMapper.selectById(id);
            mng.setReportStatus(2);//放到队列
            mng.setStatusTime(LocalDateTime.now());
            reportMngMapper.updateById(mng);
        }
        return Result.of(msglist);
    }
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "报告id", required = true),
    })
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK", response = ReportMng.class),
    })
    @ApiOperation(value = "重新生成(立刻生成报告,点每行重新生成按钮时  测试用)")
    @PostMapping("/report/generate")
    public Result generateReport(Integer id) throws IOException {
        QueryWrapper<ReportMng> mngq=new QueryWrapper<>();
        mngq.eq("id",id);
        ReportMng mng = reportMngMapper.selectById(mngq);
        reportMngService.updateReport(mng);
        return Result.ok();
    }
    @ApiOperation(value = "echarts生成")
    @PostMapping("/report/echartscreate")
    public Result echartsCreate() {
       // EchartsToPicUtil.generateEChart(EchartsToPicUtil.echartLine(true), wordCfgProperties.getPngPath(), wordCfgProperties.getOSType(), wordCfgProperties.getResourcePath());
       // EchartsToPicUtil.generateEChart(EchartsToPicUtil.echartBar(true), wordCfgProperties.getPngPath(), wordCfgProperties.getOSType(), wordCfgProperties.getResourcePath());
        return Result.of("");
    }

    @ApiOperation(value = "报告生成手动生成")
    @PostMapping("/report/reportcreate")
    public Result reportCreate() throws IOException {
        List<ReportMng> stationModellist = reportModelInterMapper.selectReReportModel(2);
        //处理场站模板 接口 生成报告
        if (stationModellist.size() > 0) {
            for (ReportMng obj : stationModellist) {
                //更新报告管理的状态为 在处理
                if (obj == null) {
                    continue;
                }
                obj.setReportStatus(3);
                reportMngMapper.updateById(obj);
                reportMngService.updateReport(obj);
            }
        }
        return Result.of("");
    }


}
