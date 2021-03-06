package com.assignment.controller;

import com.assignment.model.Assignment;
import com.assignment.model.User;
import com.assignment.model.dto.*;
import com.assignment.repository.UserRepository;
import com.assignment.service.AssignmentService;
import com.assignment.service.MailService;
import com.assignment.service.TwoFactorAuthentication;
import com.assignment.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ResourceController {
    private final static Logger logger = LoggerFactory.getLogger(ResourceController.class);
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserService userService;
    @Autowired
    private MailService mailService;
    @Autowired
    private TwoFactorAuthentication twoFactorAuthentication;

    public ResourceController() {
    }

    @RequestMapping("/")
    public String beforeLogin(Model model) {
        model.addAttribute("loginSuccess", false);
        model.addAttribute("loginFalse", true);
        model.addAttribute("members", getCounters().getMembers());
        model.addAttribute("assignments", getCounters().getAssignment());
        model.addAttribute("subjectsList", getSubjects());
        return "home";
    }

    private Count getCounters() {
        long members = userService.getAllMembersCount();
        long assignment = assignmentService.getAllAssignmentsCount();
        return new Count(members, assignment);
    }

    @RequestMapping("/home")
    public String home(Model model, HttpServletResponse response) throws IOException {
        UserGetDto currentUser = userService.getCurrentUser();

        Count counters = getCounters();
        model.addAttribute("pdfs", getAllAssignment());
        model.addAttribute("currentUser", currentUser.getEmail());
        model.addAttribute("loginSuccess", true);
        model.addAttribute("loginFalse", false);
        model.addAttribute("members", counters.getMembers());
        model.addAttribute("assignments", counters.getAssignment());
        model.addAttribute("subjectsList", getSubjects());
        return "home";
    }

    @GetMapping("/twofactorauthentication")
    public String twofactorauthentication(HttpServletResponse response, Model model) throws Exception {
        UserGetDto currentUser = userService.getCurrentUser();
        logger.info("called two factor");
        if (!currentUser.isTwoFactor()) {
            response.sendRedirect("/home");
        }
        model.addAttribute("email", currentUser.getEmail().split("@")[0]);
        if (!twoFactorAuthentication.generated)
            twoFactorAuthentication.genereteAuthenticationCode();
        return "2fa";
    }

    @PostMapping("/twofactorauthentication")
    public void twoFctorAuthentication(HttpServletResponse response, @RequestParam("code") long code) throws Exception {
        logger.info("generated verification code");
        if (twoFactorAuthentication.checkCodeValidation(code)) {
            response.sendRedirect("/home");
        } else {
            response.sendRedirect("/twofactorauthentication");
        }
    }

    @GetMapping("/resend")
    public String reSendVerificationCode() throws Exception {
        Thread.sleep(60 * 1000);
        twoFactorAuthentication.genereteAuthenticationCode();
        return "2fa";
    }

    @GetMapping("/profileImage")
    public void dislpayProfileImage(HttpServletResponse response, Model model) throws IOException {
        logger.info("display profile image");
        UserGetDto user = userService.getCurrentUser();
        response.setContentType("image/jpeg,image/png,image/jpg, image/gif");
        response.getOutputStream().write(user.getImage());
        response.getOutputStream().close();
    }

    @GetMapping("/signin")
    public String getLogin() {
        return "signin";
    }

    @GetMapping("/signup")
    public String directToSignUp() {
        return "signup";
    }

    @GetMapping("/signinerror")
    public String error(Model model) {
        logger.error("error");
        return "signin";
    }

    @PostMapping("/signup")
    public String signup(@ModelAttribute(name = "signupForm") UserDto user, @RequestParam("image") MultipartFile image, Model model) throws IOException {
        boolean b = false;
        if (!userService.checkUsername(user.getEmail())) {
            b = userService.initiateUser(user, image);
        } else {
            model.addAttribute("message", "Failed");
            return "signup";
        }
        model.addAttribute("signupmsg", "sign up is completed");
        model.addAttribute("message", "Success");
        return "signin";
    }

    @PostMapping("/assignment")
    public RedirectView uploadAssignment(@ModelAttribute(name = "assignment") AssignmentDto assignment,
                                         @RequestParam("assignmentFile") MultipartFile file, RedirectAttributes redirect) throws IOException, ParseException {
        boolean bool = assignmentService.initiateAssignment(assignment, file);
        if (bool) {
            redirect.addAttribute("condition", true);
            redirect.addAttribute("message", "Assignment upload is success!.");
        }
        return new RedirectView("home");
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam("id") String id, HttpServletResponse response) throws ChangeSetPersister.NotFoundException {
        AssignmentMultipart assignmentMultipart = assignmentService.downloadFile(Integer.parseInt(id));
        response.setContentType(assignmentMultipart.getFileType());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(assignmentMultipart.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + assignmentMultipart.getFileName() + "\"")
                .body(new ByteArrayResource(assignmentMultipart.getFile()));
    }

    @PostMapping("/mail")
    public RedirectView sendMail(@ModelAttribute(name = "mail") Mail mail, Model model, RedirectAttributes redirect) {
        boolean send = mailService.send(mail);
        if (send) {
            redirect.addAttribute("condition", send);
            redirect.addAttribute("message", "mail sent successfully");
        } else {
            redirect.addAttribute("_condition", send);
            redirect.addAttribute("message", "mail sending fail, Try within few minutes");
        }
        return new RedirectView("home");
    }

    @PostMapping("/search")
    public String getSearchAssignment(@ModelAttribute("search") Search search, Model model) {
        logger.info("" + search);
        List<Assignment> collect = getAllAssignment().stream().filter(assignment -> assignment.getSubject().equals(search.getSubject())).collect(Collectors.toList());
        collect.forEach(assignment -> System.out.println(assignment.getContentType()));
        UserGetDto currentUser = userService.getCurrentUser();

        Count counters = getCounters();
        model.addAttribute("pdfs", collect);
        model.addAttribute("currentUser", currentUser.getEmail());
        model.addAttribute("loginSuccess", true);
        model.addAttribute("loginFalse", false);
        model.addAttribute("members", counters.getMembers());
        model.addAttribute("assignments", counters.getAssignment());
        model.addAttribute("subjectsList", getSubjects());

        return "home";
    }

    @GetMapping("/all")
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @GetMapping("/logout")
    public String logout(Model model) {
        logger.info("logout");
        return "signin";
    }

    @GetMapping("/user/address/{id}")
    public AssignmentMail getCurrentUser(@PathVariable("id") long assignmentId) {
        return assignmentService.findByAssignmentId(assignmentId);
    }

    private List<String> getSubjects() {
        List<String> subjects = assignmentService.getSubjects();
        return subjects;

    }

    private List<Assignment> getAllAssignment() {
        List<Assignment> allAssignments = assignmentService.getAllAssignments();
        allAssignments.forEach(
                assignment -> {
                    String[] split = assignment.getContentType().split("/", 2);
                    if (split[1].equals("pdf")) {
                        assignment.setContentType("PDF");
                    } else if (split[1].equals("msword") || split[1].equals("octet-stream")) {
                        assignment.setContentType("DOC");
                    } else if (split[1].equals("jpeg") || split[1].equals("png") || split[1].equals("jpg")) {
                        assignment.setContentType("IMG");
                    } else {
                        assignment.setContentType("OTHER");
                    }
                }
        );
        return allAssignments;
    }

    @GetMapping("/processingUrl")
    public void processingUrl(HttpServletResponse response) throws Exception {
        if (true) {
            response.sendRedirect("/twofactorauthentication");
        } else {
            response.sendRedirect("/home");
        }
    }
   @GetMapping("/exception")
    public String handellingException(){
        return "exception";
   }

   @GetMapping("/ioexception")
    public String handellingIoException(){
        return "ioexception";
   }
}
