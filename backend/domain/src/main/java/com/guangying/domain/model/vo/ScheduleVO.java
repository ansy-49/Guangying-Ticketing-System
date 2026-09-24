package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 场次展示对象
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleVO implements Serializable {

    private Long id;
    private Long movieId;
    private Long cinemaId;
    private String cinemaNm;
    private String cinemaAddr;
    private String hallName;
    private String showDate;
    private String showTime;
    private String endTime;
    private String lang;
    private Integer totalSeats;
    private Integer availableSeats;
    private BigDecimal price;
}
