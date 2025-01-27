package org.example.ikproje.service;

import lombok.RequiredArgsConstructor;
import org.example.ikproje.dto.request.AssignShiftToUserRequestDto;
import org.example.ikproje.dto.response.UserShiftResponseDto;
import org.example.ikproje.entity.Shift;
import org.example.ikproje.entity.User;
import org.example.ikproje.entity.UserShift;
import org.example.ikproje.entity.enums.EState;
import org.example.ikproje.entity.enums.EUserRole;
import org.example.ikproje.exception.ErrorType;
import org.example.ikproje.exception.IKProjeException;
import org.example.ikproje.repository.UserShiftRepository;
import org.example.ikproje.utility.JwtManager;
import org.example.ikproje.view.VwBreakSummary;
import org.example.ikproje.view.VwUserActiveShift;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserShiftService {
	private final UserShiftRepository userShiftRepository;
	private final ShiftService shiftService;
	private final UserService userService;
	private final JwtManager jwtManager;
	private final BreakService breakService;
	
	public boolean assignShiftToUser(AssignShiftToUserRequestDto dto) {
		Optional<Shift> optShift = shiftService.getShiftById(dto.shiftId());
		if (optShift.isEmpty()) {
			throw new IKProjeException(ErrorType.SHIFT_NOT_FOUND);
		}
		Shift shift = optShift.get();
		Optional<Long> optCompanyManagerId = jwtManager.validateToken(dto.token());
		if (optCompanyManagerId.isEmpty()) {
			throw new IKProjeException(ErrorType.INVALID_TOKEN);
		}
		Optional<User> optCompanyManager = userService.findById(optCompanyManagerId.get());
		Optional<User> optPersonel = userService.findById(dto.userId());
		if (optPersonel.isEmpty() || optCompanyManager.isEmpty()) {
			throw new IKProjeException(ErrorType.USER_NOTFOUND);
		}
		User companyManager = optCompanyManager.get();
		User personel = optPersonel.get();
		if (companyManager.getUserRole() == EUserRole.EMPLOYEE) {
			throw new IKProjeException(ErrorType.UNAUTHORIZED);
		}
		if (dto.startDate().isAfter(dto.endDate())) {
			throw new IKProjeException(ErrorType.SHIFT_TIME_ERROR);
		}
		List<UserShift> personelsActiveShifts =
				userShiftRepository.findByUserIdAndState(personel.getId(), EState.ACTIVE);
		
		if (isDateRangeOverlapping(personelsActiveShifts, dto.startDate(), dto.endDate())) {
			throw new IKProjeException(ErrorType.SHIFT_DATE_OVERLAP);
		}
		
		UserShift userShift =
				UserShift.builder().shiftId(dto.shiftId()).userId(personel.getId()).startDate(dto.startDate())
				         .endDate(dto.endDate()).build();
		userShiftRepository.save(userShift);
		
		return true;
	}
	
	
	//Burada tarihlerin çakışıp çakışmadığını kontrol ediyorum. Eğer çakışıyorlarsa ana metotta (assignShiftToUser)
	// hata yolluyorum.
	private boolean isDateRangeOverlapping(List<UserShift> activeShifts, LocalDate startDate, LocalDate endDate) {
		return activeShifts.stream()
		                   .anyMatch(shift -> (startDate.isBefore(shift.getEndDate()) || startDate.isEqual(shift.getEndDate())) && (endDate.isAfter(shift.getStartDate()) || endDate.isEqual(shift.getStartDate())));
	}
	
	public List<VwUserActiveShift> getActiveShiftDetails(String token) {
		User user = userService.getUserByToken(token);
		
		// Kullanıcının tüm aktif vardiyalarını al
		List<UserShift> activeShifts = userShiftRepository.findByUserIdAndState(user.getId(), EState.ACTIVE);
		
		if (activeShifts.isEmpty()) {
			throw new IKProjeException(ErrorType.SHIFT_NOT_FOUND);
		}
		
		// Her bir aktif vardiya için VwUserActiveShift nesneleri oluştur
		List<VwUserActiveShift> shiftList = activeShifts.stream().map(activeShift -> {
			Shift shift = shiftService.getShiftById(activeShift.getShiftId())
			                          .orElseThrow(() -> new IKProjeException(ErrorType.SHIFT_NOT_FOUND));
			
			List<VwBreakSummary> breakList = breakService.findByShiftId(shift.getId()).stream()
			                                             .map(b -> new VwBreakSummary(b.getName(),
			                                                                          LocalTime.parse(b.getStartTime()), LocalTime.parse(b.getEndTime())))
			                                             .toList();
			
			return VwUserActiveShift.builder().userId(user.getId()).shiftName(shift.getName())
			                        .shiftStartTime(LocalTime.parse(shift.getStartTime()))
			                        .shiftEndTime(LocalTime.parse(shift.getEndTime()))
			                        .startDate(activeShift.getStartDate()).endDate(activeShift.getEndDate())
			                        .breaks(breakList).build();
		}).toList();
		
		return shiftList;
	}
	
	// Personelin vardiyasının ve molalarının detaylarını getiren metot.
	public VwUserActiveShift getActiveShiftDetailsByUserId(Long userId, String token) {
		Optional<Long> optCompanyManagerId = jwtManager.validateToken(token);
		if (optCompanyManagerId.isEmpty()) {
			throw new IKProjeException(ErrorType.INVALID_TOKEN);
		}
		Optional<User> optCompanyManager = userService.findById(optCompanyManagerId.get());
		if (optCompanyManager.isEmpty()) {
			throw new IKProjeException(ErrorType.USER_NOTFOUND);
		}
		User companyManager = optCompanyManager.get();
		if (companyManager.getUserRole() == EUserRole.EMPLOYEE) {
			throw new IKProjeException(ErrorType.UNAUTHORIZED);
		}
		
		// Burada personel'in state'i active olan shift'ini aldım.
		UserShift activeShift = userShiftRepository.findByUserIdAndState(userId, EState.ACTIVE).stream().findFirst()
		                                           .orElseThrow(() -> new IKProjeException(ErrorType.SHIFT_NOT_FOUND));
		// Burada yukarıda aldığım shift'in detaylarını (adı, başlama zamanı, bitiş zamanı) aldım.
		Shift shift = shiftService.getShiftById(activeShift.getShiftId())
		                          .orElseThrow(() -> new IKProjeException(ErrorType.SHIFT_NOT_FOUND));
		// Burada o shift'e atanmış molaları bir List içerisine aldım.
		List<VwBreakSummary> breakList = breakService.findByShiftId(shift.getId()).stream()
		                                             .map(b -> new VwBreakSummary(b.getName(),
		                                                                          LocalTime.parse(b.getStartTime()),
		                                                                          LocalTime.parse(b.getEndTime())))
		                                             .toList();
		// Burada da Vw'in içerisini yukarıda almış olduğum bilgilerle doldurdum.
		
		
		return new VwUserActiveShift(userId, shift.getName(), LocalTime.parse(shift.getStartTime()),
		                             LocalTime.parse(shift.getEndTime()), activeShift.getStartDate(),
		                             activeShift.getEndDate(), breakList);
	}
	
	
	public List<User> getPersonelListByCompanyId(String token) {
		Optional<Long> optionalCompanyManagerId = jwtManager.validateToken(token);
		if (optionalCompanyManagerId.isEmpty()) {
			throw new IKProjeException(ErrorType.INVALID_TOKEN);
		}
		User companyManager = userService.findById(optionalCompanyManagerId.get())
		                                 .orElseThrow(() -> new IKProjeException(ErrorType.USER_NOTFOUND));
		List<User> personelList = userService.findAllPersonelByCompanyId(companyManager.getCompanyId());
		return personelList;
	}
	
	public List<UserShiftResponseDto> getPersonelShiftList(String token) {
		User companyManager = userService.getUserByToken(token);
		
		
		List<UserShift> userShiftList = userShiftRepository.findAllByState(EState.ACTIVE);
		List<UserShiftResponseDto> responseDtoList = new ArrayList<>();
		
		for (UserShift userShift : userShiftList){
			
			Optional<User> optionalUser = userService.findById(userShift.getUserId());
			Optional<Shift> optionalShift = shiftService.findById(userShift.getShiftId());
			if(optionalUser.isEmpty() ) {
				throw new IKProjeException(ErrorType.USER_NOTFOUND);
			}
			if(optionalShift.isEmpty()){
				throw new IKProjeException(ErrorType.SHIFT_NOT_FOUND);
			}
			if(!optionalShift.get().getCompanyId().equals(companyManager.getCompanyId())){
				continue;
			}
			
			List<VwBreakSummary> breakList = breakService.findByShiftId(optionalShift.get().getId()).stream()
			                                             .map(b -> new VwBreakSummary(b.getName(), LocalTime.parse(b.getStartTime()), LocalTime.parse(b.getEndTime())))
			                                             .toList();
			
			UserShiftResponseDto dto = UserShiftResponseDto.builder()
			                                               .id(userShift.getId())
			                                               .shiftName(optionalShift.get().getName())
			                                               .personelName(optionalUser.get().getFirstName())
			                                               .personelSurname(optionalUser.get().getLastName())
			                                               .startDate(userShift.getStartDate())
			                                               .endDate(userShift.getEndDate())
			                                               .breaks(breakList)
			                                               .build();
			responseDtoList.add(dto);
		}
		return responseDtoList;
	}
}