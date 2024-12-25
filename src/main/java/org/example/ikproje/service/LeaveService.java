package org.example.ikproje.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.ikproje.dto.request.NewLeaveRequestDto;
import org.example.ikproje.entity.Leave;
import org.example.ikproje.entity.LeaveDetails;
import org.example.ikproje.entity.User;
import org.example.ikproje.entity.UserDetails;
import org.example.ikproje.entity.enums.EGender;
import org.example.ikproje.entity.enums.ELeaveStatus;
import org.example.ikproje.entity.enums.ELeaveType;
import org.example.ikproje.entity.enums.EUserRole;
import org.example.ikproje.exception.ErrorType;
import org.example.ikproje.exception.IKProjeException;
import org.example.ikproje.mapper.LeaveMapper;
import org.example.ikproje.repository.LeaveRepository;
import org.example.ikproje.utility.JwtManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveService {
    private final LeaveRepository leaveRepository;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final JwtManager jwtManager;
    private final EmailService emailService;
    private final LeaveDetailsService leaveDetailsService;
    
    
    public Boolean createNewLeaveRequest(NewLeaveRequestDto dto){
        double multiplierForAnnualLeave;
        User personel = getUserByToken(dto.token());
        if (personel.getUserRole()==EUserRole.COMPANY_MANAGER){
            throw new IKProjeException(ErrorType.UNAUTHORIZED);
        }
        if (personel.getGender()== EGender.MALE&&dto.leaveType()== ELeaveType.DOGUM_IZNI){
            throw new IKProjeException(ErrorType.UNAUTHORIZED);
        }
        UserDetails personelDetails = userDetailsService.findByUserId(personel.getId());
        long numberOfDaysWorked = ChronoUnit.DAYS.between( personelDetails.getHireDate(), LocalDate.now());
        if (numberOfDaysWorked<365){
            multiplierForAnnualLeave=0;
        }
        else if (numberOfDaysWorked < 1825) {
            multiplierForAnnualLeave=1;
        }
        else if (numberOfDaysWorked < 2555) {
            multiplierForAnnualLeave=1.5;
        }
        else if (numberOfDaysWorked < 3650) {
            multiplierForAnnualLeave=2;
        }
        else {
            multiplierForAnnualLeave=2.5;
        }
        
        Leave leave = LeaveMapper.INSTANCE.fromNewLeaveDto(dto);
        LeaveDetails leaveDetails=LeaveDetails.builder().build();
        leaveDetails.setNumberOfDaysRemainingFromAnnualLeave((int) (ELeaveType.YILLIK_IZIN.getNumberOfLeaveDays()*multiplierForAnnualLeave));
        leaveDetails.setNumberOfDaysRemainingFromMarriageLeave(ELeaveType.EVLILIK_IZNI.getNumberOfLeaveDays());
        leaveDetailsService.save(leaveDetails);
        if(personel.getGender()==EGender.FEMALE)
        {
            leaveDetails.setNumberOfDaysRemainingFromMaternityLeave(ELeaveType.DOGUM_IZNI.getNumberOfLeaveDays());
        }
        
        long numberOfLeaveDays = ChronoUnit.DAYS.between(dto.startDate(),dto.endDate());
        if (dto.leaveType()==ELeaveType.YILLIK_IZIN){
            if (numberOfLeaveDays>leaveDetails.getNumberOfDaysRemainingFromAnnualLeave()){
                throw new IKProjeException(ErrorType.LEAVE_ERROR);
            }
        }
        else if(dto.leaveType()==ELeaveType.EVLILIK_IZNI||dto.leaveType()==ELeaveType.DOGUM_IZNI){
            if (numberOfLeaveDays>dto.leaveType().getNumberOfLeaveDays()){
                throw new IKProjeException(ErrorType.LEAVE_ERROR);
            }
        }
        leave.setCompanyId(personel.getCompanyId());
        leave.setUserId(personel.getId());
        leave.setLeaveStatus(ELeaveStatus.PENDING);
        leave.setStatusDate(LocalDate.now());
        leaveRepository.save(leave);
        leaveDetails.setLeaveId(leave.getId());
        leaveDetailsService.save(leaveDetails);
        return true;
    }


    //Şirket yöneticisine gelen izin istekleri
    public List<Leave> getAllLeaveRequests(String token){
        User companyManager = getUserByToken(token);
        if(!companyManager.getUserRole().equals(EUserRole.COMPANY_MANAGER)) throw new IKProjeException(ErrorType.UNAUTHORIZED);
        return leaveRepository.findAllByLeaveStatusAndCompanyId(ELeaveStatus.PENDING,companyManager.getCompanyId());
    }

    //personelin talepte bulunduğu izin listesi
    public List<Leave> getPersonelRequestLeaveList(String token){
        User personel = getUserByToken(token);
        if(!personel.getUserRole().equals(EUserRole.EMPLOYEE)) throw new IKProjeException(ErrorType.UNAUTHORIZED);
        return leaveRepository.findAllByLeaveStatusAndUserId(ELeaveStatus.PENDING,personel.getId());
    }
    
    
  
    @Transactional
    public Boolean approveLeaveRequest(String token,Long leaveId){
        User companyManager = getUserByToken(token);
        if(!companyManager.getUserRole().equals(EUserRole.COMPANY_MANAGER)) throw new IKProjeException(ErrorType.UNAUTHORIZED);
        Leave leave = leaveRepository.findById(leaveId).orElseThrow(()->new IKProjeException(ErrorType.PAGE_NOT_FOUND));
        LeaveDetails leaveDetails = leaveDetailsService.findByLeaveId(leaveId).orElseThrow(() -> new IKProjeException(ErrorType.PAGE_NOT_FOUND));
        long numberOfLeaveDays = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate());
        updateLeaveDetails(leaveDetails,leave.getLeaveType(),numberOfLeaveDays);
        leave.setLeaveStatus(ELeaveStatus.APPROVED);
        leave.setStatusDate(LocalDate.now());
        leaveRepository.save(leave);
        String personelMail = userService.findById(leave.getUserId()).get().getEmail();
        emailService.sendApprovedLeaveNotificationEmail(personelMail,leave);
        return true;
    }
    
    private void updateLeaveDetails(LeaveDetails leaveDetails, ELeaveType leaveType, long numberOfLeaveDays) {
        if (leaveType == ELeaveType.YILLIK_IZIN) {
            leaveDetails.setNumberOfDaysRemainingFromAnnualLeave(
                    (int) (leaveDetails.getNumberOfDaysRemainingFromAnnualLeave() - numberOfLeaveDays)
            );
        } else if (leaveType == ELeaveType.EVLILIK_IZNI) {
            leaveDetails.setNumberOfDaysRemainingFromMarriageLeave(
                    (int) (leaveDetails.getNumberOfDaysRemainingFromMarriageLeave() - numberOfLeaveDays)
            );
        } else if (leaveType == ELeaveType.DOGUM_IZNI) {
            leaveDetails.setNumberOfDaysRemainingFromMaternityLeave(
                    (int) (leaveDetails.getNumberOfDaysRemainingFromMaternityLeave() - numberOfLeaveDays)
            );
        } else {
            throw new IKProjeException(ErrorType.LEAVE_ERROR);
        }
    }
    
    public Boolean rejectLeaveRequest(String token,Long leaveId,String rejectionMessage){
        
        User companyManager = getUserByToken(token);
        if(!companyManager.getUserRole().equals(EUserRole.COMPANY_MANAGER)) throw new IKProjeException(ErrorType.UNAUTHORIZED);
        Leave leave = leaveRepository.findById(leaveId).orElseThrow(()->new IKProjeException(ErrorType.PAGE_NOT_FOUND));
        leave.setLeaveStatus(ELeaveStatus.REJECTED);
        leave.setStatusDate(LocalDate.now());
        leaveRepository.save(leave);
        String personelMail = userService.findById(leave.getUserId()).get().getEmail();
        emailService.sendRejectedLeaveNotificationEmail(personelMail,leave,rejectionMessage);
        return true;
    }

    // geçersiz token bilgisi olayına bak !!!
    private User getUserByToken(String token){
        Long userId = jwtManager.validateToken(token).orElseThrow(() -> new IKProjeException(ErrorType.INVALID_TOKEN));
        return userService.findById(userId).orElseThrow(() -> new IKProjeException(ErrorType.USER_NOTFOUND));
    }
}