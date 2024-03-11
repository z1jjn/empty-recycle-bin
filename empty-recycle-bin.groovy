import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean
import com.atlassian.jira.issue.AttachmentManager
import java.time.LocalDateTime
import com.atlassian.jira.timezone.TimeZoneManager
import java.time.temporal.ChronoUnit
import com.atlassian.jira.project.ProjectManager

IssueManager issueManager = ComponentAccessor.getComponent(IssueManager)
UserManager userManager = ComponentAccessor.getComponent(UserManager)
ProjectManager projectManager = ComponentAccessor.getComponent(ProjectManager)
CommentManager commentManager = ComponentAccessor.getComponent(CommentManager)
AttachmentManager attachmentManager = ComponentAccessor.getComponent(AttachmentManager)
MutableIssue issue = issueManager.getIssueObject("LINK_TO_A_SUPPORT_TICKET_TO_MONITOR_RESULTS")
ApplicationUser user = userManager.getUserByKey("ADMINISTRATOR_ACCOUNT")
StringBuilder sb = new StringBuilder()

sb <<= "\\# REMOVAL OF TICKETS THAT HAVE BEEN IN THE RECYCLE BIN FOR MORE THAN 6 MONTHS \n"
sb <<= "\\# CURRENT DATE: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy EE z"))}\n"
sb <<= "\\# SERVICE STARTED: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))}\n"

try {
    File logger = new File("${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_result.txt")
    logger.createNewFile()
    PrintWriter l = new PrintWriter(new BufferedWriter(new FileWriter(logger, true)))
    projectManager.getAllProjectCategories().each{ projectCategory ->
    if (projectCategory.id != 10900)
        {
			projectManager.getProjectObjectsFromProjectCategory(projectCategory.id).each{ projects ->
				l.println(projects.key);
                l.println()
                if (projectManager.getCurrentCounterForProject(projects.id) > 0)
                    {
						l.println("Deleting sub-tasks...")
                        issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projects.id)).each{ issueKey ->

                            if (issueKey.getSecurityLevelId() == 12300 && !issueKey.summary.equals("DELETED-HOLDER") && issueKey && issueKey.getIssueType().isSubTask() == true)
                                {
                                    def updated = issueKey.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                    if (ChronoUnit.MONTHS.between(updated, LocalDateTime.now()) > 6)
                                        {
                                            issueManager.deleteIssueNoEvent(issueKey)
                                            l.println(issueKey)
                                        }
                                }
                        }
                        l.println("Done deleting sub-tasks.")
                        l.println("Deleting parent tickets...")
                        issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projects.id)).each{ issueKey ->

                            if (issueKey.getSecurityLevelId() == 12300 && !issueKey.summary.equals("DELETED-HOLDER") && issueKey && issueKey.getIssueType().isSubTask() == false)
                                {
                                    def updated = issueKey.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                    if (ChronoUnit.MONTHS.between(updated, LocalDateTime.now()) > 6)
                                        {
                                            issueManager.deleteIssueNoEvent(issueKey)
                                            l.println(issueKey)
                                        }
                                }
                        }
                        l.println("Done deleting main tickets.")
                        }
                        l.println()
                        l.println()
            }
         }
    }
	
    l.close()
    def loggerBean = new CreateAttachmentParamsBean.Builder()
        .file(logger)
        .filename("${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_result.txt")
        .contentType("text/plain")
        .author(user)
        .issue(issue)
        .build()
        attachmentManager.createAttachment(loggerBean)
} catch(error) {
    File errorLog = new File("${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_error.txt")
    errorLog.createNewFile()
	PrintWriter e = new PrintWriter(new BufferedWriter(new FileWriter(errorLog, true)))
	e.println("# BEGIN ERROR");
	e.println(error)
    e.println("# END ERROR");
    e.close()
    def errorBean = new CreateAttachmentParamsBean.Builder()
        .file(errorLog)
        .filename("${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_error.txt")
        .contentType("text/plain")
        .author(user)
        .issue(issue)
        .build()
        attachmentManager.createAttachment(errorBean)
} finally {
    sb <<= "\\# SERVICE TERMINATED: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))}\n"
    def issueAttachment = attachmentManager.getAttachments(issue).get(attachmentManager.getAttachments(issue).size() - 1);

    if(issueAttachment.getFilename() == "${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_result.txt")
        {
            sb <<= "\\# RESULT: [^${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_result.txt]\n"
        }
    if(issueAttachment.getFilename() == "${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_error.txt")
        {
            sb <<= "\\# ERROR: [^${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))}_recycle_bin_deletion_error.txt]\n"
        }
}

commentManager.create(issue, user, sb.toString(), true) 
return null
