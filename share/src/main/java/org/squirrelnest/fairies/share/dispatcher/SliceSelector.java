package org.squirrelnest.fairies.share.dispatcher;

import org.squirrelnest.fairies.domain.Record;
import org.squirrelnest.fairies.local.domain.SliceDetail;
import org.squirrelnest.fairies.local.enumeration.SliceStateEnum;
import org.squirrelnest.fairies.share.dto.SliceBitmap;
import org.squirrelnest.fairies.share.dispatcher.model.SliceDownloadTarget;

import java.util.HashMap;
import java.util.Map;

/**
 * 多线程下载任务分派与状态设置类，线程安全
 * Created by Inoria on 2019/3/22.
 */
public class SliceSelector {

    private final double SLICE_HOLD_RATE_LOW_LEVEL = 0.1;

    private final Map<Record, SliceBitmap> holderSlices;

    private final SliceDetail mySlices;

    /**
     * 记录文件提供方对应的最近一次下载分片的花费时间。
     */
    private final Map<Record, Long> holderLatestDownloadTime;

    public SliceSelector(Map<Record, SliceBitmap> holderSlices, SliceDetail mySlices) {
        this.holderSlices = holderSlices;
        this.mySlices = mySlices;
        this.holderLatestDownloadTime = new HashMap<>(16);
    }

    public Long getLastDownloadTimeAndRememberNewData(Record holder, Long newDownloadTime) {
        Long lastDownloadTime = holderLatestDownloadTime.getOrDefault(holder, -1L);
        holderLatestDownloadTime.put(holder, newDownloadTime);
        return lastDownloadTime;
    }

    public synchronized SliceStateEnum beginDownloadSlice(int index) {
        SliceStateEnum oldState = mySlices.getSliceState(index);
        mySlices.setSliceState(index, SliceStateEnum.DOWNLOADING);
        return oldState;
    }

    public synchronized void downloadFailed(int index) {
        mySlices.setSliceState(index, SliceStateEnum.LACK_AND_FOUND);
    }

    public synchronized void downloadSuccess(int index) {
        mySlices.setSliceState(index, SliceStateEnum.HAVING);
    }

    public synchronized Boolean sliceExists(int index) {
        return mySlices.getSliceState(index).haveSlice();
    }

    public synchronized SliceDownloadTarget selectSliceIndex() {
        Double sliceHoldRate = mySlices.sliceHoldRate();
        if (sliceHoldRate < SLICE_HOLD_RATE_LOW_LEVEL) {
            return expectSpeedFastest();
        } else {
            return expectHoldRateLeast();
        }
    }

    public boolean downloadFinished() {
        return mySlices.hasAllFile();
    }

    public void updateSliceState(Record holder, SliceBitmap bitmap) {
        this.holderSlices.put(holder, bitmap);
    }


    /**
     * 刚开始下载时（拥有分片较少时），优先下载期望下载速度最快的分片
     */
    private SliceDownloadTarget expectSpeedFastest() {

    }

    /**
     * 持有一定分片之后, 优先下载所有持有者节点中最少的分片
     */
    private SliceDownloadTarget expectHoldRateLeast() {

    }
}
