/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fr.sciforma;

import com.fr.sciforma.input.CSVFileInputImpl;
import com.fr.sciforma.input.LineFileInput;
import com.fr.sciforma.manager.ResourceManager;
import com.fr.sciforma.manager.ResourceManagerImpl;
import com.sciforma.psnext.api.AccessException;
import com.sciforma.psnext.api.DataViewRow;
import com.sciforma.psnext.api.DatedData;
import com.sciforma.psnext.api.DoubleDatedData;
import com.sciforma.psnext.api.Global;
import com.sciforma.psnext.api.InvalidDataException;
import com.sciforma.psnext.api.LockException;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Project;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;
import com.sciforma.psnext.api.StringDatedData;
import com.sciforma.psnext.api.Task;
import com.sciforma.psnext.api.Timesheet;
import com.sciforma.psnext.api.TimesheetAssignment;
import com.sciforma.psnext.util.Log;
import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Logger;
import org.pmw.tinylog.writers.ConsoleWriter;
import org.pmw.tinylog.writers.FileWriter;

/**
 *
 * @author lahou
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    private final static String NUMBER = "1.1";

    private final static String PROGRAM = "JIRA-TEMPO / SCIFORMA";

    private static String IP;
    private static String CONTEXTE;
    private static String USER;
    private static String PWD;

    private static String FLOW_1 = "FLOW_1";
    private static String FLOW_2 = "FLOW_2";
    private static String FLOW_3 = "FLOW_3";
    private static String INTEGRATION = "INTEGRATION";
    private static Boolean INTEGRATION_RUN = false;
    
    private static Properties properties;

    public static Session mSession;
    public static SimpleDateFormat formatter;

    public static Calendar today;

    private static List<Project> projectList;
    private static ResourceManager publishedResourceManager;
    private static HashMap<String, Project> projectBySAPId;

    private static Boolean error;

    public static void main(String[] args) {
        Configurator.currentConfig().writer(new FileWriter("log_" + args[0] + ".txt")).addWriter(new ConsoleWriter()).activate();
        Logger.info("[main][" + PROGRAM + "][V" + NUMBER + "] Start API: " + new Date());
        if (args.length == 1) {
            try {
                initialisation();
                connexion();
                if (args[0].equals(FLOW_1)) {
                    processResourcesChecks();
                }

                if (args[0].equals(FLOW_2)) {
                    processProjectsAndTasksChecks();
                }

                if (args[0].equals(FLOW_3)) {
                    processTimesheetsTransferAndChecks();
                }

                if (args[0].equals(INTEGRATION)) {
                    checkRunIntegration();
                    if(INTEGRATION_RUN){
                        loadData();
                        processTSFilling();
                        processTSSubmission();
                    }
                }

                mSession.logout();
                Logger.info("[main][" + PROGRAM + "][V" + NUMBER + "] End API: " + new Date());
            } catch (PSException ex) {
                if (ex instanceof LockException) {
                    LockException lex = (LockException) ex;
                    Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
                } else {
                    Logger.error(ex);
                }
            }
        } else {
            Logger.error("#00: Invalid Argument !");
            System.exit(-1);
        }
        System.exit(0);
    }

    private static void initialisation() {
        properties = new Properties();
        FileInputStream in;
        formatter = new SimpleDateFormat("yyyyMMdd");
        try {
            in = new FileInputStream(System.getProperty("user.dir") + System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "psconnect.properties");
            properties.load(in);
            in.close();
        } catch (FileNotFoundException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        } catch (IOException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        } catch (NullPointerException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        }
    }

    private static void connexion() {

        try {
            USER = properties.getProperty("sciforma.user");
            PWD = properties.getProperty("sciforma.pwd");
            IP = properties.getProperty("sciforma.ip");
            CONTEXTE = properties.getProperty("sciforma.ctx");

            Logger.info("Initialisation de la Session:" + new Date());
            String url = IP + "/" + CONTEXTE;
            mSession = new Session(url);
            mSession.login(USER, PWD.toCharArray());
            Logger.info("Connecté: " + new Date() + " à l'instance " + CONTEXTE);
        } catch (PSException ex) {
            Logger.error("Erreur dans la connection de ... " + CONTEXTE);
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
            System.exit(-1);
        } catch (NullPointerException ex) {
            Logger.error("Erreur dans la connection de ... " + CONTEXTE, ex);
            System.exit(-1);
        }
    }

    private static void processResourcesChecks() {
        Logger.info("************ Start of processResourcesChecks ************");
        String dvName = "jRES";
        try {
            Logger.info("Clean of DataView ... " + dvName);
            cleanDataView(dvName);
            LineFileInput<String[]> lineFileInput = new CSVFileInputImpl(properties.getProperty("import.file.flow1"));
            Logger.info("Read file : " + properties.getProperty("import.file.flow1"));
            Global g = new Global();
            g.lock();
            Boolean ignoreHeader = true;
            for (String[] line : lineFileInput.readAll()) {
                if (!(line.length == 4)) {
                    Logger.warn("la ligne <" + Arrays.deepToString(line) + "> n'a pas le nombre d'élements attendu <4>, mais <" + line.length + ">");
                    continue;
                } else {
                    if (!ignoreHeader) {
                        Logger.info("Traitement de la ligne <" + Arrays.deepToString(line) + ">");
                        populateDataViewJRES(dvName, line, g);
                    }
                }
                ignoreHeader = false;
            }
            g.save(true);
        } catch (BusinessException ex) {
            Logger.error(ex);
        } catch (PSException e) {
            if (e instanceof LockException) {
                LockException lex = (LockException) e;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
                System.exit(-1);
            }
            Logger.error(e);
        }
        Logger.info("************ End of processResourcesChecks ************");
    }

    private static void processProjectsAndTasksChecks() {
        Logger.info("************ Start of processProjectsAndTasksChecks ************");
        String dvName = "jTASK";
        try {
            Logger.info("Clean of DataView ... " + dvName);
            cleanDataView(dvName);
            LineFileInput<String[]> lineFileInput = new CSVFileInputImpl(properties.getProperty("import.file.flow2"));
            Logger.info("Read file : " + properties.getProperty("import.file.flow2"));
            Global g = new Global();
            g.lock();
            Boolean ignoreHeader = true;
            for (String[] line : lineFileInput.readAll()) {
                if (!(line.length == 3)) {
                    Logger.warn("la ligne <" + Arrays.deepToString(line) + "> n'a pas le nombre d'élements attendu <3>, mais <" + line.length + ">");
                    continue;
                } else {
                    if (!ignoreHeader) {
                        Logger.info("Traitement de la ligne <" + Arrays.deepToString(line) + ">");
                        populateDataViewJTASK(dvName, line, g);
                    }
                }
                ignoreHeader = false;
            }
            g.save(true);
        } catch (PSException e) {
            if (e instanceof LockException) {
                LockException lex = (LockException) e;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
                System.exit(-1);
            }
            Logger.error(e);
        } catch (BusinessException ex) {
            Logger.error(ex);
        }
        Logger.info("************ End of processProjectsAndTasksChecks ************");
    }

    private static void processTimesheetsTransferAndChecks() {
        Logger.info("************ Start of processTimesheetsTransferAndChecks ************");
        today = Calendar.getInstance();
        String dvName = "jTIMETRACKING";
        String dvName2 = "jSUBMISSION";
        try {
            Logger.info("Clean of DataView ... " + dvName);
            cleanDataView(dvName);
            Logger.info("Clean of DataView ... " + dvName2);
            cleanDataView(dvName2);
            LineFileInput<String[]> lineFileInput = new CSVFileInputImpl(properties.getProperty("import.file.flow3"));
            Logger.info("Read file : " + properties.getProperty("import.file.flow3"));
            Global g = new Global();
            g.lock();
            Boolean ignoreHeader = true;
            for (String[] line : lineFileInput.readAll()) {
                if (!(line.length == 5)) {
                    Logger.warn("la ligne <" + Arrays.deepToString(line) + "> n'a pas le nombre d'élements attendu <3>, mais <" + line.length + ">");
                    continue;
                } else {
                    if (!ignoreHeader) {
                        Logger.info("Traitement de la ligne <" + Arrays.deepToString(line) + ">");
                        populateDataViewJTIMETRACKING(dvName, line, g);
                    }
                }
                ignoreHeader = false;
            }
            g.save(true);
            g.lock();
            populateDataViewJSUBMISSION(dvName2, dvName, g);
            g.save(true);
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        } catch (BusinessException ex) {
            Logger.error(ex);
        }
        Logger.info("************ End of processTimesheetsTransferAndChecks ************");
    }

    private static void cleanDataView(String dvName) {
        Global g = new Global();
        try {
            Logger.info("************ Lock of Global Category ************");
            g.lock();
            List vpbh = mSession.getDataViewRowList(dvName, g);
            Iterator vpbhit = vpbh.iterator();
            while (vpbhit.hasNext()) {
                DataViewRow dvr = (DataViewRow) vpbhit.next();
                Logger.info("Remove ...");
                dvr.remove();
            }
            g.save(true);
            Logger.info("************ Unlock and save of Global Category ************");
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        } catch (Exception ex) {
            Logger.error(ex);
        }
    }

    private static void populateDataViewJRES(String dvName, String[] line, Global g) {
        try {
            DataViewRow new_dvr = new DataViewRow(dvName, g, DataViewRow.CREATE);
            new_dvr.setStringField("email", line[0].trim());
            new_dvr.setStringField("first_name", line[1].trim());
            new_dvr.setStringField("last_name", line[2].trim());
            new_dvr.setStringField("extraction_date", line[3].trim());
            new_dvr.setDateField("import_date", today.getTime());
        } catch (TechnicalException ex) {
            Logger.error(ex);
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        }
    }

    private static void populateDataViewJTASK(String dvName, String[] line, Global g) {
        try {
            DataViewRow new_dvr = new DataViewRow(dvName, g, DataViewRow.CREATE);
            new_dvr.setStringField("project_SAP_ID", line[0].trim());
            new_dvr.setStringField("task_jID", line[1].trim());
            new_dvr.setStringField("extraction_date", line[2].trim());
        } catch (TechnicalException ex) {
            Logger.error(ex);
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        }
    }

    private static void populateDataViewJTIMETRACKING(String dvName, String[] line, Global g) {
        try {
            DataViewRow new_dvr = new DataViewRow(dvName, g, DataViewRow.CREATE);
            new_dvr.setStringField("project_SAP_ID", line[0].trim());
            new_dvr.setStringField("task_jID", line[1].trim());
            new_dvr.setStringField("Res_ID", line[2].trim());
            new_dvr.setDateField("TS_Date", formatter.parse(line[3].trim()));
            new_dvr.setDoubleField("effort", Double.parseDouble(line[4].trim()));
            try {
                g.save(false);
            } catch (PSException ex) {
                Logger.warn("Invalid data => " + Arrays.deepToString(line));
                new_dvr.remove();
            }
        } catch (TechnicalException ex) {
            Logger.error(ex);
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        } catch (ParseException ex) {
            Logger.error(ex);
        } catch (NumberFormatException ex) {
            Logger.error(ex);
        }
    }

    private static void populateDataViewJSUBMISSION(String jsubmission, String dvName, Global g) {
        try {
            List pl = mSession.getDataViewRowList(dvName, g);
            ArrayList<String> listRes = new ArrayList<String>();;
            Iterator pit = pl.iterator();
            while (pit.hasNext()) {
                DataViewRow dvr = (DataViewRow) pit.next();
                String email = dvr.getStringField("Res_ID").trim();
                if (!listRes.contains(email)) {
                    listRes.add(email);
                }
            }
            for (String res : listRes) {
                DataViewRow new_dvr = new DataViewRow(jsubmission, g, DataViewRow.CREATE);
                new_dvr.setStringField("email", res);
            }
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        }

    }

    private static void checkRunIntegration() {
        Logger.info("************ Start of checkRunIntegration ************");
        try {

            Global g = new Global();
            List pl = mSession.getDataViewRowList("JIRA I/F Admin", g);
            Iterator pit = pl.iterator();
            while (pit.hasNext()) {
                DataViewRow dvr = (DataViewRow) pit.next();
                Logger.info("Field TTRCKFlag :" + dvr.getBooleanField("TTRCKFlag"));
                INTEGRATION_RUN = dvr.getBooleanField("TTRCKFlag");
            }
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            } else {
                Logger.error(ex);
            }
        }
        Logger.info("************ End of checkRunIntegration ************");
    }

    private static void processTSFilling() {
        Logger.info("************ Start of processTSFilling ************");
        Project project;
        Task task;
        Resource resource = null;

        try {
            Global g = new Global();
            g.lock();
            List pl = mSession.getDataViewRowList("jTIMETRACKING", g);
            Iterator pit = pl.iterator();
            while (pit.hasNext()) {
                project = null;
                task = null;
                error = false;
                DataViewRow dvr = (DataViewRow) pit.next();
                Logger.info("Process line ..." + dvr.getStringField("project_SAP_ID") + " | " + dvr.getStringField("task_jID") + " | " + dvr.getStringField("Res_ID") + " | " + dvr.getDateField("TS_Date") + " | " + dvr.getDoubleField("effort"));

                Logger.info("Search resource ...");
                if (resource == null || !resource.getStringField("Email Address 1").equals(dvr.getStringField("Res_ID"))) {
                    resource = publishedResourceManager.findResourceByCode(dvr.getStringField("Res_ID"));
                }
                if (resource == null) {
                    logError(dvr, "00", "Resource not found for " + dvr.getStringField("Res_ID"));
                } else {
                    Logger.info("Resource find => " + resource.getStringField("Name"));
                    if (!resource.getStringField("Status").equals("ACTIVE")) {
                        logError(dvr, "04", resource.getStringField("Name") + " is " + resource.getStringField("Status"));
                    }
                }

                Logger.info("Search project ...");
                if (projectBySAPId.containsKey(dvr.getStringField("project_SAP_ID"))) {
                    project = (Project) projectBySAPId.get(dvr.getStringField("project_SAP_ID"));
                } else {
                    project = null;
                }

                if (project != null) {
                    Logger.info("Project found => " + project.getStringField("Name"));
                    project.open(true);
                    if (!project.getBooleanField("Closed")) {
                        List<Task> taskList = project.getTaskOutlineList();
                        for (Iterator<Task> iterator = taskList.iterator(); iterator.hasNext();) {
                            Task t = iterator.next();
                            if (t.getStringField("Name").contains(dvr.getStringField("task_jID"))) {
                                task = t;
                            }
                        }

                        List<String> team = project.getListField("Project Team (Users)");
                        Boolean findTeam = false;
                        for (Iterator<String> teamIt = team.iterator(); teamIt.hasNext();) {
                            String teamMember = teamIt.next();
                            if (teamMember.equals(resource.getStringField("Name"))) {
                                findTeam = true;
                            }
                        }

                        if (!findTeam) {
                            logError(dvr, "15", resource.getStringField("Name") + " is not project member of " + dvr.getStringField("project_SAP_ID"));
                        }

                        if (task != null) {

                            if (task.getBooleanField("Closed")) {
                                logError(dvr, "13", "Task is closed for " + dvr.getStringField("task_jID"));
                            }

                            if (task.getBooleanField("Is Parent")) {
                                logError(dvr, "16", "Parent task for " + dvr.getStringField("task_jID"));
                            }

                            if (!task.getBooleanField("Allow My Work Add")) {
                                logError(dvr, "12", "Self-assignment not allowed for " + dvr.getStringField("task_jID"));
                            }
                            Boolean findOrga = false;
                            List<String> perfOrga = task.getListField("Performing Organizations");
                            for (Iterator<String> perfOrgaIt = perfOrga.iterator(); perfOrgaIt.hasNext();) {
                                String orga = perfOrgaIt.next();
                                if (orga.equals(resource.getStringField("Organization"))) {
                                    findOrga = true;
                                }
                            }

                            if (!findOrga) {
                                logError(dvr, "14", resource.getStringField("Name") + " is not granted in performing organization for " + dvr.getStringField("project_SAP_ID"));
                            }

                            if (!error) {
                                Logger.info("No error, process Timesheet ...");
                                //Timesheet ts1;
                                //ts1 = mSession.getTimesheet(resource, dvr.getDateField("TS_Date"));
                                //dumpForTesting(ts1);

                                applyTimeSheet(resource, task, dvr);

                                //dumpForTesting(ts1);
                                Logger.info("end process Timesheet ...");
                            }
                        } else {
                            logError(dvr, "03", "Task not found for " + dvr.getStringField("task_jID"));
                        }
                    } else {
                        logError(dvr, "02", "Projet is closed for " + dvr.getStringField("project_SAP_ID"));
                    }
                    project.close();
                } else {
                    logError(dvr, "01", "Project not found for " + dvr.getStringField("project_SAP_ID"));
                }
                Logger.info("Process next line ...");
            }
            g.save(true);
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            }
        } catch (BusinessException ex) {
            Logger.error(ex);
        } catch (TechnicalException ex) {
            Logger.error(ex);
        }
        Logger.info("************ End of processTSFilling ************");
    }

    private static void processTSSubmission() {
        try {
            Logger.info("************ Start of processTSSubmission ************");
            Global g = new Global();
            g.lock();
            List dvJSub = mSession.getDataViewRowList("jSUBMISSION", g);
            Iterator jSubIt = dvJSub.iterator();
            while (jSubIt.hasNext()) {
                DataViewRow dvrJSub = (DataViewRow) jSubIt.next();
                String email = dvrJSub.getStringField("email").trim();
                Logger.info("Process ... " + email);
                String submission_status = "OK";
                String submission_error_c = "";
                String submission_message = "";
                List dvJTime = mSession.getDataViewRowList("jTIMETRACKING", g);
                Iterator jTimeIt = dvJTime.iterator();
                while (jTimeIt.hasNext()) {
                    DataViewRow dvrjTime = (DataViewRow) jSubIt.next();
                    if (email.equals(dvrjTime.getStringField("Res_ID"))) {
                        if (dvrjTime.getStringField("fill_status").equals("TD")) {
                            submission_status = dvrjTime.getStringField("fill_status");
                            submission_error_c = dvrjTime.getStringField("fill_error_code");
                            submission_message = dvrjTime.getStringField("fill_message");
                        }
                    }
                }
                dvrJSub.setStringField("submission_status", submission_status);
                dvrJSub.setStringField("submission_error_c", submission_error_c);
                dvrJSub.setStringField("submission_message", submission_message);
            }
            g.save(true);
            Logger.info("************ End of processTSSubmission ************");
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            }
        }
    }

    private static void loadData() {
        Logger.info("************ Start of loadData ************");
        publishedResourceManager = new ResourceManagerImpl(mSession).withUsePublishedResources(true);
        try {
            projectList = mSession.getProjectList(Project.VERSION_PUBLISHED, Project.READWRITE_ACCESS);
            int size = projectList.size();
            int cpt = 1;
            projectBySAPId = new HashMap<String, Project>();
            for (Iterator<Project> pit = projectList.iterator(); pit.hasNext();) {
                Logger.info("Load project " + cpt + "/" + size);
                Project p = pit.next();
                //if (p.getStringField("Name").equals("Roadmap Axis Payment 2018")) {
                p.open(true);
                if (!p.getStringField("SAP_ID").isEmpty()) {
                    projectBySAPId.put(p.getStringField("SAP_ID"), p);
                    //Logger.info("Add code => " + p.getStringField("SAP_ID"));
                }
                p.close();
                //}
                cpt++;
            }
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            }
        } catch (TechnicalException ex) {
            Logger.error(ex);
        }
        Logger.info("************ End of loadData ************");
    }

    private static void applyTimeSheet(Resource resource, Task t, DataViewRow dvr) {
        try {
            Logger.info("Application ongoing ....... Resource: " + resource.getStringField("Name") + " for " + t.getStringField("Name") + " with hours: " + dvr.getDoubleField("effort"));
            Boolean findDoubleDatedData = false;

            Calendar dateToDay = Calendar.getInstance();
            dateToDay.setTime(dvr.getDateField("TS_Date"));

            Calendar startCal = (Calendar) dateToDay.clone();
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = (Calendar) dateToDay.clone();
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            Timesheet time = mSession.getTimesheet(resource, startCal.getTime(), endCal.getTime());

            if (time.getStatus() == Timesheet.STATUS_APPROVED) {
                logError(dvr, "22", "Timesheet status is approved");
            }
            if (time.getStatus() == Timesheet.STATUS_REVIEWED) {
                logError(dvr, "21", "Timesheet status is reviewed");
            }
            if (time.getStatus() == Timesheet.STATUS_LOCKED) {
                logError(dvr, "23", "Timesheet status is locked");
            }
            if (time.getStatus() == Timesheet.STATUS_SUBMITTED) {
                logError(dvr, "24", "Timesheet status is submited");
            }
            if (!error) {
                if (time.getStatus() == Timesheet.STATUS_WORKING || time.getStatus() == Timesheet.STATUS_NONE) {
                    //On récupère la liste des activitée assignée ? la feuille de temps en cours
                    List<TimesheetAssignment> ltimeAss = time.getTimesheetAssignmentList();
                    TimesheetAssignment tsAss = null;
                    for (TimesheetAssignment timeAss : ltimeAss) {
                        if (timeAss.getStringField("Name").equals(t.getStringField("Name"))) // On regarde si la tache existe
                        {
                            findDoubleDatedData = true;
                            tsAss = timeAss;
                        }
                    }
                    if (tsAss == null) // Si aucune tache trouvé, on s'autoaffecte la tache correspondante
                    {
                        tsAss = time.addAssignment(t);
                    }
                    List<DoubleDatedData> lddd = tsAss.getDatedData("Actual Effort", DatedData.DAY, startCal.getTime(), endCal.getTime());
                    //On commence par chercher, s'il y a déjà des heures pointé sur le jour concerné.
                    Boolean dataAdded = false;
                    for (DoubleDatedData ddd : lddd) {
                        ddd.setData(dvr.getDoubleField("effort") + ddd.getData());
                        Logger.info("Update Timesheet (" + t.getStringField("Name") + ") for " + ddd.getData());
                        dataAdded = true;
                    }
                    //Si aucun pointage pour le jour en question, on crée un nouveau DoubleDateData
                    if (!findDoubleDatedData || !dataAdded) {
                        DoubleDatedData ddd;
                        Calendar c = Calendar.getInstance();
                        c.setTime(dvr.getDateField("TS_Date"));
                        c.add(Calendar.DATE, 1);
                        ddd = new DoubleDatedData(dvr.getDoubleField("effort"), dvr.getDateField("TS_Date"), c.getTime());
                        lddd.add(ddd);
                        Logger.info("New entry on the Timesheet (" + t.getStringField("Name") + ") for " + ddd.getData());
                    }
                    tsAss.clearDatedData("Actual Effort", startCal.getTime(), endCal.getTime());
                    try {
                        tsAss.updateDatedData("Actual Effort", lddd);
                        tsAss.setDatedData("Actual Effort", lddd);
                    } catch (AccessException ex) {
                        tsAss.setDatedData("Actual Effort", lddd);
                    }
                    Logger.info("Save timesheet ...");
                    time.save();
                } else {
                    logError(dvr, "25", "Error in Timesheet status");
                }
            }
        } catch (NullPointerException ex) {
            Logger.error(ex);
        } catch (AccessException ex) {
            Logger.error(ex);
        } catch (InvalidDataException ex) {
            Logger.error(ex);
        } catch (LockException ex) {
            Logger.error(ex);
        } catch (PSException ex) {
            Logger.error(ex);
        }
    }

    private static void logError(DataViewRow dvr, String code, String message) {
        try {
            String error_code = "fill_error_code";
            String error_message = "fill_message";
            String status = "fill_status";

            dvr.setStringField(error_code, code);
            dvr.setStringField(error_message, message);
            dvr.setStringField(status, "TD");

            Logger.warn("#" + dvr.getStringField(error_code) + " => " + dvr.getStringField(error_message));

            error = true;
        } catch (PSException ex) {
            if (ex instanceof LockException) {
                LockException lex = (LockException) ex;
                Logger.error("================= Lock by " + lex.getLockingUser() + " =================");
            }else{
                Logger.error(ex);
            }
        }
    }

}
