import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
import os

def set_cell_border(cell, **kwargs):
    """
    Set cell borders
    kwargs: top, bottom, left, right
    values: dict(sz=12, val='single', color='000000')
    """
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = tcPr.first_child_found_in("w:tcBorders")
    if tcBorders is None:
        tcBorders = OxmlElement('w:tcBorders')
        tcPr.append(tcBorders)
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        edge_data = kwargs.get(edge)
        if edge_data:
            tag = 'w:{}'.format(edge)
            element = tcBorders.find(qn(tag))
            if element is None:
                element = OxmlElement(tag)
                tcBorders.append(element)
            for key, val in edge_data.items():
                element.set(qn('w:{}'.format(key)), str(val))

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), fill_hex)
    tcPr.append(shd)

def add_page_number(run):
    fldChar1 = OxmlElement('w:fldChar')
    fldChar1.set(qn('w:fldCharType'), 'begin')
    instrText = OxmlElement('w:instrText')
    instrText.set(qn('xml:space'), 'preserve')
    instrText.text = "PAGE"
    fldChar2 = OxmlElement('w:fldChar')
    fldChar2.set(qn('w:fldCharType'), 'separate')
    fldChar3 = OxmlElement('w:fldChar')
    fldChar3.set(qn('w:fldCharType'), 'end')
    r = run._r
    r.append(fldChar1)
    r.append(instrText)
    r.append(fldChar2)
    r.append(fldChar3)

def create_report():
    doc = docx.Document()

    # Set page margins: 1.25 in left (for binding), 1 in right, top, bottom
    for section in doc.sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.25)
        section.right_margin = Inches(1.0)
        
        # Add page numbering to footer
        footer = section.footer
        f_p = footer.paragraphs[0]
        f_p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        f_p.paragraph_format.space_after = Pt(0)
        f_run = f_p.add_run("Vellore Institute of Technology, Chennai  |  Page ")
        f_run.font.name = "Times New Roman"
        f_run.font.size = Pt(10)
        f_run.font.color.rgb = RGBColor(100, 100, 100)
        add_page_number(f_p.add_run())

    # Configure Normal Style
    style_normal = doc.styles['Normal']
    font_normal = style_normal.font
    font_normal.name = 'Times New Roman'
    font_normal.size = Pt(12)
    font_normal.color.rgb = RGBColor(0, 0, 0)
    style_normal.paragraph_format.line_spacing = 1.5
    style_normal.paragraph_format.space_after = Pt(6)
    style_normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

    def p(text="", bold=False, italic=False, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=0, space_after=6, font_size=12):
        par = doc.add_paragraph()
        par.alignment = align
        par.paragraph_format.line_spacing = 1.5
        par.paragraph_format.space_before = Pt(space_before)
        par.paragraph_format.space_after = Pt(space_after)
        if text:
            run = par.add_run(text)
            run.bold = bold
            run.italic = italic
            run.font.name = 'Times New Roman'
            run.font.size = Pt(font_size)
            run.font.color.rgb = RGBColor(0, 0, 0)
        return par

    def h1(text):
        doc.add_page_break()
        par = doc.add_paragraph()
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        par.paragraph_format.space_before = Pt(18)
        par.paragraph_format.space_after = Pt(18)
        par.paragraph_format.line_spacing = 1.5
        run = par.add_run(text.upper())
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(16)
        run.font.color.rgb = RGBColor(0, 32, 96) # Academic Navy
        return par

    def h2(text):
        par = doc.add_paragraph()
        par.alignment = WD_ALIGN_PARAGRAPH.LEFT
        par.paragraph_format.space_before = Pt(12)
        par.paragraph_format.space_after = Pt(6)
        par.paragraph_format.line_spacing = 1.5
        run = par.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(13.5)
        run.font.color.rgb = RGBColor(0, 32, 96)
        return par

    def h3(text):
        par = doc.add_paragraph()
        par.alignment = WD_ALIGN_PARAGRAPH.LEFT
        par.paragraph_format.space_before = Pt(8)
        par.paragraph_format.space_after = Pt(4)
        par.paragraph_format.line_spacing = 1.5
        run = par.add_run(text)
        run.bold = True
        run.italic = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(12)
        run.font.color.rgb = RGBColor(20, 20, 20)
        return par

    def add_table_data(headers, rows, caption=""):
        if caption:
            cp = doc.add_paragraph()
            cp.alignment = WD_ALIGN_PARAGRAPH.LEFT
            cp.paragraph_format.space_before = Pt(8)
            cp.paragraph_format.space_after = Pt(4)
            c_run = cp.add_run(caption)
            c_run.bold = True
            c_run.font.name = 'Times New Roman'
            c_run.font.size = Pt(11)

        table = doc.add_table(rows=len(rows) + 1, cols=len(headers))
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = True

        # Header Row
        hdr_cells = table.rows[0].cells
        for i, header_text in enumerate(headers):
            hdr_cells[i].text = header_text
            set_cell_background(hdr_cells[i], 'EBF1F5')
            p = hdr_cells[i].paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.space_before = Pt(2)
            run = p.runs[0]
            run.bold = True
            run.font.name = 'Times New Roman'
            run.font.size = Pt(10.5)

        # Body Rows
        border_kwargs = dict(
            top=dict(sz=4, val='single', color='D0D0D0'),
            bottom=dict(sz=4, val='single', color='D0D0D0'),
            left=dict(sz=4, val='single', color='D0D0D0'),
            right=dict(sz=4, val='single', color='D0D0D0')
        )
        for r_idx, row_data in enumerate(rows):
            row_cells = table.rows[r_idx + 1].cells
            for c_idx, cell_value in enumerate(row_data):
                row_cells[c_idx].text = str(cell_value)
                set_cell_border(row_cells[c_idx], **border_kwargs)
                p = row_cells[c_idx].paragraphs[0]
                p.paragraph_format.space_after = Pt(2)
                p.paragraph_format.space_before = Pt(2)
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT if c_idx != 0 else WD_ALIGN_PARAGRAPH.CENTER
                if p.runs:
                    p.runs[0].font.name = 'Times New Roman'
                    p.runs[0].font.size = Pt(10)

        # Add spacing after table
        sp = doc.add_paragraph()
        sp.paragraph_format.space_before = Pt(2)
        sp.paragraph_format.space_after = Pt(6)

    def add_callout_box(title, content):
        table = doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        cell = table.cell(0, 0)
        set_cell_background(cell, 'F8F9FA')
        set_cell_border(cell, 
            top=dict(sz=6, val='single', color='002060'),
            bottom=dict(sz=6, val='single', color='002060'),
            left=dict(sz=18, val='single', color='002060'),
            right=dict(sz=6, val='single', color='002060')
        )
        cp = cell.paragraphs[0]
        cp.paragraph_format.space_before = Pt(4)
        cp.paragraph_format.space_after = Pt(2)
        trun = cp.add_run(f"Figure / Technical Illustration: {title}\n")
        trun.bold = True
        trun.font.name = 'Times New Roman'
        trun.font.size = Pt(11)
        trun.font.color.rgb = RGBColor(0, 32, 96)

        crun = cp.add_run(content)
        crun.font.name = 'Times New Roman'
        crun.font.size = Pt(10)
        crun.font.color.rgb = RGBColor(40, 40, 40)
        
        sp = doc.add_paragraph()
        sp.paragraph_format.space_after = Pt(6)

    print("Building Title Page...")
    # ==========================================
    # PRELIMINARY PAGES
    # ==========================================

    # PAGE 1: TITLE PAGE
    p("BACKEND ENGINEERING INTERNSHIP", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=24, space_after=12, font_size=15)
    p("DESIGN AND DEVELOPMENT OF BIDDRIVE CORE:\nA REAL-TIME RIDE-FARE AUCTION ENGINE", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=12, space_after=24, font_size=16)
    
    p("AN INDUSTRIAL INTERNSHIP TRAINING REPORT", italic=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=12, space_after=12, font_size=12)
    p("Submitted by", align=WD_ALIGN_PARAGRAPH.CENTER, space_before=6, space_after=6, font_size=12)
    p("[YOUR FULL NAME]", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=4, space_after=2, font_size=13)
    p("[YOUR REGISTER NUMBER]", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=2, space_after=18, font_size=13)
    
    p("[COURSE CODE — INDUSTRIAL INTERNSHIP]", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=6, space_after=18, font_size=12)
    p("in partial fulfillment of the requirements for the award of the degree of", align=WD_ALIGN_PARAGRAPH.CENTER, space_before=6, space_after=6, font_size=12)
    p("BACHELOR OF TECHNOLOGY", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=4, space_after=2, font_size=14)
    p("in", align=WD_ALIGN_PARAGRAPH.CENTER, space_before=2, space_after=2, font_size=12)
    p("ELECTRONICS AND COMPUTER ENGINEERING", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=2, space_after=24, font_size=14)
    
    p("VELLORE INSTITUTE OF TECHNOLOGY, CHENNAI", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=4, font_size=14)
    p("School: [VERIFY OFFICIAL SCHOOL NAME]", align=WD_ALIGN_PARAGRAPH.CENTER, space_before=2, space_after=12, font_size=12)
    p("[MONTH AND YEAR OF SUBMISSION: JUNE 2026]", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=12, space_after=0, font_size=12)

    # PAGE 2: DECLARATION
    doc.add_page_break()
    p("DECLARATION BY THE CANDIDATE", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    p("I hereby declare that the industrial internship training report entitled \"BACKEND ENGINEERING INTERNSHIP: DESIGN AND DEVELOPMENT OF BIDDRIVE CORE — A REAL-TIME RIDE-FARE AUCTION ENGINE\" submitted to Vellore Institute of Technology, Chennai, in partial fulfillment of the requirements for the award of the degree of Bachelor of Technology in Electronics and Computer Engineering, is a bonafide record of the work carried out by me during the period May 2026 – June 2026 under the supervision of my industry mentor at Twozo Technologies.")
    
    p("I further declare that the content of this report has not been submitted previously, in part or in full, to any other university, institute, or examining body for the award of any degree, diploma, fellowship, or other similar title.")
    
    p("Location: Chennai\nDate: [INSERT DATE]", space_before=36, space_after=24)
    p("[YOUR FULL NAME]\nSignature of the Candidate", bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT, space_before=24, space_after=0)

    # PAGE 3: INTERNSHIP COMPLETION CERTIFICATE
    doc.add_page_break()
    p("INTERNSHIP COMPLETION CERTIFICATE", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    p("[RESERVED FOR OFFICIAL INTERNSHIP COMPLETION CERTIFICATE ISSUED BY TWOZO TECHNOLOGIES]", bold=True, italic=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=48, space_after=24)
    p("Please insert the authorized, scanned copy of the genuine Internship Completion Certificate provided by Twozo Technologies on corporate letterhead, bearing the official seal and the signature of the authorized signatory, HR Manager, or Technical Director.", align=WD_ALIGN_PARAGRAPH.CENTER)

    # PAGE 4: BONAFIDE CERTIFICATE
    doc.add_page_break()
    p("BONAFIDE CERTIFICATE", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    p("This is to certify that this industrial internship training report entitled \"BACKEND ENGINEERING INTERNSHIP: DESIGN AND DEVELOPMENT OF BIDDRIVE CORE — A REAL-TIME RIDE-FARE AUCTION ENGINE\" is the bonafide work of [YOUR FULL NAME] ([YOUR REGISTER NUMBER]), a student of Bachelor of Technology in Electronics and Computer Engineering at Vellore Institute of Technology, Chennai, who carried out internship training at Twozo Technologies during the summer period of May 2026 to June 2026.")
    
    p("This report is submitted in partial fulfillment of the requirements for the course [COURSE CODE — INDUSTRIAL INTERNSHIP] as per the curriculum regulations prescribed by Vellore Institute of Technology, Chennai.", space_after=36)
    
    p("Faculty Guide:                                                    Head of the Department / Dean:\n\n___________________________                                ___________________________\n[FACULTY GUIDE NAME]                                       [HOD / DEAN NAME]\nVellore Institute of Technology                            Vellore Institute of Technology\nChennai, Tamil Nadu                                        Chennai, Tamil Nadu", space_before=36, space_after=36)
    
    p("Internal Examiner: _______________________      External Examiner: _______________________", space_before=24, space_after=0)

    # PAGE 5: ACKNOWLEDGEMENT
    doc.add_page_break()
    p("ACKNOWLEDGEMENT", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    p("I express my profound gratitude to the management of Twozo Technologies for granting me the invaluable opportunity to complete my industrial summer internship as a Backend Engineering Intern during May–June 2026. Working on high-throughput backend services and real-time distributed systems has been an enriching professional milestone.")
    
    p("I extend my deepest appreciation to my Industry Mentor, [INDUSTRY MENTOR NAME], and the entire engineering team at Twozo Technologies. Their technical mentorship, patient guidance, code review rigor, and practical insights into high-concurrency architecture, database tuning, and distributed systems significantly shaped the design and development of the BidDrive Core project.")
    
    p("I am sincerely grateful to my Faculty Guide, [FACULTY GUIDE NAME], for continuous academic supervision, constructive feedback, and steadfast encouragement throughout the internship period. His/Her insightful suggestions ensured that this report maintains high technical and academic standards.")
    
    p("I place on record my heartfelt gratitude to the Head of the Department, faculty members, and the administrative staff of the School of [VERIFY OFFICIAL SCHOOL NAME] at Vellore Institute of Technology, Chennai, for providing the foundational academic training in computer architecture, database engineering, and software design principles that empowered me to undertake this industry project.")
    
    p("Finally, I convey my deepest love and gratitude to my family and peers for their constant support, understanding, and encouragement during the course of this internship.")
    
    p("[YOUR FULL NAME]\nRegister Number: [YOUR REGISTER NUMBER]", bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT, space_before=18)

    # PAGE 6: TABLE OF CONTENTS
    doc.add_page_break()
    p("TABLE OF CONTENTS", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    toc_data = [
        ["Declaration by the Candidate", "ii"],
        ["Internship Completion Certificate", "iii"],
        ["Bonafide Certificate", "iv"],
        ["Acknowledgement", "v"],
        ["List of Symbols and Abbreviations", "vii"],
        ["Abstract", "viii"],
        ["CHAPTER 1 — INTRODUCTION", "1"],
        ["   1.1 Introduction", "1"],
        ["   1.2 Overview of Backend Engineering", "2"],
        ["   1.3 Internship Organization Overview", "3"],
        ["   1.4 Internship Objectives and Scope", "4"],
        ["   1.5 Project Overview: BidDrive Core", "5"],
        ["   1.6 Project Requirements and Engineering Challenges", "6"],
        ["   1.7 Project Technology Overview", "7"],
        ["CHAPTER 2 — PROJECT DESIGN AND IMPLEMENTATION", "9"],
        ["   2.1 Project Workflow", "9"],
        ["   2.2 System Architecture", "11"],
        ["   2.3 Backend Application Design", "13"],
        ["   2.4 REST API Development", "15"],
        ["   2.5 Redis-Based Auction State Management", "17"],
        ["   2.6 Bid Validation and Atomic Processing", "19"],
        ["   2.7 Auction Lifecycle Management", "21"],
        ["   2.8 Database Design and Transactional Persistence", "23"],
        ["   2.9 Real-Time Communication", "25"],
        ["   2.10 No-Bid Fallback and Retry Workflow", "27"],
        ["CHAPTER 3 — TECHNOLOGY AND SYSTEM SPECIFICATIONS", "29"],
        ["   3.1 Java and Spring Boot", "29"],
        ["   3.2 PostgreSQL and Spring Data JPA", "30"],
        ["   3.3 Redis and Time-to-Live Management", "31"],
        ["   3.4 Concurrency Control", "32"],
        ["   3.5 Transaction Management and Database Locking", "33"],
        ["   3.6 Real-Time Event Delivery", "34"],
        ["   3.7 Development and Testing Tools", "35"],
        ["CHAPTER 4 — DEVELOPMENT STANDARDS, TESTING AND PERFORMANCE ANALYSIS", "36"],
        ["   4.1 Backend Development Practices", "36"],
        ["   4.2 API Validation and Error Handling", "37"],
        ["   4.3 Data Consistency and Reliability", "38"],
        ["   4.4 Functional and Integration Testing", "39"],
        ["   4.5 Concurrency Testing", "40"],
        ["   4.6 Load Testing Using k6", "41"],
        ["   4.7 Performance Results and Threshold Analysis", "42"],
        ["   4.8 Database and Application Bottlenecks", "44"],
        ["   4.9 Network and Resource Constraints", "46"],
        ["   4.10 Optimization Opportunities and Future Improvements", "48"],
        ["CHAPTER 5 — CONCLUSION", "50"],
        ["   5.1 Conclusion", "50"],
        ["   5.2 Technical Learning Outcomes", "51"],
        ["   5.3 Future Scope", "52"],
        ["REFERENCES", "53"],
        ["APPENDIX A — API REFERENCE", "55"],
        ["APPENDIX B — PERFORMANCE TEST SUMMARY", "57"],
        ["APPENDIX C — ADDITIONAL ARCHITECTURE DIAGRAMS", "59"]
    ]
    add_table_data(["Title / Chapter Section", "Page"], toc_data)

    # PAGE 7: LIST OF SYMBOLS AND ABBREVIATIONS
    doc.add_page_break()
    p("LIST OF SYMBOLS AND ABBREVIATIONS", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    abbr_data = [
        ["API", "Application Programming Interface"],
        ["BCrypt", "Blowfish Cryptographic Hash Function"],
        ["CAS", "Compare-And-Swap (Atomic Instruction)"],
        ["CPU", "Central Processing Unit"],
        ["DTO", "Data Transfer Object"],
        ["HTTP", "Hypertext Transfer Protocol"],
        ["HTTPS", "Hypertext Transfer Protocol Secure"],
        ["IDE", "Integrated Development Environment"],
        ["IDOR", "Insecure Direct Object Reference"],
        ["JPA", "Java Persistence API"],
        ["JSON", "JavaScript Object Notation"],
        ["JWT", "JSON Web Token (RFC 7519)"],
        ["JVM", "Java Virtual Machine"],
        ["JDK", "Java Development Kit"],
        ["JDBC", "Java Database Connectivity"],
        ["JUnit", "Java Unit Testing Framework"],
        ["k6", "Grafana Distributed Load Testing Framework"],
        ["ORM", "Object-Relational Mapping"],
        ["OTP", "One-Time Password (Boarding PIN)"],
        ["P95", "95th Percentile Response Time"],
        ["P99", "99th Percentile Response Time"],
        ["PostgreSQL", "PostgreSQL Object-Relational Database"],
        ["RBAC", "Role-Based Access Control"],
        ["RCA", "Root Cause Analysis"],
        ["Redis", "Remote Dictionary Server (In-Memory Key-Value Store)"],
        ["REST", "Representational State Transfer"],
        ["RPS", "Requests Per Second"],
        ["SLA", "Service Level Agreement"],
        ["SQL", "Structured Query Language"],
        ["SSE", "Server-Sent Events"],
        ["STOMP", "Simple Text Oriented Messaging Protocol"],
        ["TOCTOU", "Time-of-Check to Time-of-Use Race Condition"],
        ["TTL", "Time-To-Live (Cache Expiration Duration)"],
        ["UI", "User Interface"],
        ["VU", "Virtual User (Load Testing Simulated Client)"],
        ["WSS", "WebSocket Secure Protocol"]
    ]
    add_table_data(["Abbreviation", "Full Form"], abbr_data)

    # PAGE 8: ABSTRACT
    doc.add_page_break()
    p("ABSTRACT", bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18, font_size=14)
    
    p("The industrial summer internship undertaken at Twozo Technologies during the period May 2026 – June 2026 provided comprehensive exposure to modern backend engineering through the architectural design, implementation, and empirical evaluation of BidDrive Core—a real-time, high-throughput ride-fare reverse auction engine. Traditional ride-hailing services heavily depend on opaque algorithmic surge pricing, which frequently creates market frictions between passengers and drivers. BidDrive Core addresses these dynamics by introducing a decentralized mobility marketplace where passengers propose baseline fares and nearby drivers compete by counter-bidding progressively lower fares within a time-bounded reverse-auction window.")
    
    p("The backend architecture is implemented in Java 21 LTS utilizing the Spring Boot 3.4 framework, structured as a clean layered monolith with microservices-ready domain boundaries. Ephemeral auction states, high-frequency driver geospatial tracking, and real-time lowest-bid compare-and-swap operations are handled in-memory using Redis 7 to decouple latency-sensitive operations from disk-backed storage. Long-term transactional persistence, audit logs, user profiles, and completed trip ledgers are reliably maintained in PostgreSQL 16 using Spring Data JPA and version-controlled Flyway schema migrations. Real-time state synchronizations and live bid tickers are published over STOMP WebSockets with JWT-authenticated channel interceptors.")
    
    p("Comprehensive quality assurance was conducted across the testing pyramid, encompassing 31 JUnit 5 unit and slice tests, multi-user end-to-end browser workflows using Playwright with isolated browser contexts, and distributed stress testing using Grafana k6 simulating 1,000 concurrent Virtual Users (750 drivers and 250 passengers). The recorded baseline stress tests exposed acute architectural bottlenecks, including Tomcat OS thread starvation, single-socket Redis queueing, database connection pool exhaustion, and row lock contention. Through systematic performance engineering—including Java 21 Project Loom Virtual Threads, Lettuce connection pooling, atomic Redis Lua compare-and-swap scripting, and composite B-Tree indexing—the system achieved substantial latency reductions and throughput scalability. This report details the technical principles, design patterns, bottleneck analyses, and empirical outcomes derived from this intensive backend engineering internship.")

    print("Building Chapter 1...")
    # ==========================================
    # CHAPTER 1 — INTRODUCTION
    # ==========================================
    h1("CHAPTER 1 — INTRODUCTION")
    
    h2("1.1 Introduction")
    p("Modern software engineering has witnessed a profound transformation in how distributed digital services are designed, operated, and scaled. In contemporary software architectures, the backend serves as the core operational foundation, orchestrating complex business rules, enforcing data integrity, executing financial transactions, and coordinating communication across diverse client interfaces. Backend services are responsible for managing the entire lifecycle of client requests, translating high-level user actions into deterministic transactional operations, and guaranteeing that system state remains consistent even under extreme concurrent access.")
    
    p("Among the most demanding domains of modern backend engineering are real-time, on-demand mobility and transportation platforms. Applications such as ride-hailing, dynamic carpooling, and urban logistics operate under stringent physical and temporal constraints. Unlike traditional e-commerce platforms where request processing can be decoupled via asynchronous queues with relaxed latency bounds, mobility platforms require immediate coordination between spatially distributed actors. When a passenger requests a ride, the platform must ingest real-time geospatial telemetries, discover candidate drivers within tight spherical radii, coordinate dynamic pricing or competitive bidding, and guarantee that driver allocation is mutually exclusive and race-condition-free.")
    
    p("This industrial internship report examines the design, engineering, and performance evaluation of BidDrive Core, a specialized real-time ride-fare auction engine developed during the summer internship at Twozo Technologies. The project addresses fundamental limitations in conventional ride-hailing platforms by synthesizing the speed of algorithmic instant dispatch with the price transparency of time-bounded reverse auctions.")

    h2("1.2 Overview of Backend Engineering")
    p("Backend engineering encompasses the disciplines, frameworks, patterns, and infrastructure required to build and maintain the server-side logic of software systems. A production-grade backend application comprises several architectural layers, each fulfilling distinct functional responsibilities:")
    
    p("1. Client-Server Communication & API Gateways: Manages incoming network protocols (HTTP/HTTPS, WebSockets, gRPC), handles TLS termination, parses structured JSON payloads, and validates incoming client parameters against strict validation constraints.")
    p("2. Authentication & Authorization: Verifies caller identities through cryptographic tokens (such as JSON Web Tokens), extracts security principals, and enforces Role-Based Access Control (RBAC) to protect sensitive domain resources against unauthorized modification or privilege escalation.")
    p("3. Business Logic & Domain Services: Contains the core domain models, state machines, and algorithmic rules that govern application behavior. This layer operates independently of presentation details and encapsulates the invariants of the system.")
    p("4. In-Memory State & Caching Tiers: Employs high-speed in-memory data stores (such as Redis) to manage transient, rapidly mutating state that would overwhelm disk-backed databases if persisted synchronously.")
    p("5. Relational Persistence & Transactional Ledgers: Utilizes robust relational database management systems (such as PostgreSQL) to guarantee ACID properties (Atomicity, Consistency, Isolation, Durability) for durable entity records, financial balances, and historical audit logs.")
    p("6. Concurrency & Execution Runtimes: Manages how the underlying virtual machine or operating system allocates execution threads to handle thousands of overlapping client connections without resource exhaustion.")

    add_callout_box("Figure 1.1: General Architecture of a Backend Application",
        "Client (Mobile / Web App)\n"
        "      │ (HTTPS / WSS Request)\n"
        "      ▼\n"
        "Ingress & Security Gateway (Spring Security, JWT Filter, RBAC)\n"
        "      │ (Authenticated Request Context)\n"
        "      ▼\n"
        "REST API Controller Layer (Request Validation & DTO Transformation)\n"
        "      │ (Method Invocations)\n"
        "      ▼\n"
        "Service / Business Logic Layer (State Machine, Fare Calculation, Invariants)\n"
        "      │                               │\n"
        "      ▼ (Hot Path: Sub-ms Ephemeral)   ▼ (Cold Path: ACID Durability)\n"
        "In-Memory Cache (Redis 7)        Relational Database (PostgreSQL 16)\n"
        "  - Driver Locations (GEO)         - Passengers, Drivers, Bids, Rides\n"
        "  - 90s Auction Ephemeral Hash     - Financial Wallets & Audit Logs\n"
        "  - Atomic Lua Scripting           - Composite B-Tree Indexes"
    )

    h2("1.3 Internship Organization Overview")
    p("The industrial internship training was undertaken at Twozo Technologies, a technology enterprise focused on software engineering solutions, enterprise platforms, and scalable digital systems. During the internship period from May 2026 to June 2026, the intern was assigned to the engineering division as a Backend Engineering Intern.")
    p("Twozo Technologies provides an environment characterized by rigorous engineering standards, code reviews, architectural discussions, and a strong emphasis on clean code, automated testing, and performance profiling. The internship program was structured to immerse the candidate in end-to-end backend development workflows, bridging theoretical computer science concepts—such as multi-threading, concurrency control, database indexing, and network protocols—with industrial production practices.")

    h2("1.4 Internship Objectives and Scope")
    p("The primary objective of the internship was to develop a deep, practical understanding of modern backend engineering by designing, implementing, and stress-testing a mission-critical real-time backend engine. Specifically, the objectives encompassed:")
    p("• Developing proficiency in Java 21 LTS and Spring Boot 3.4 for enterprise application engineering.")
    p("• Designing and implementing RESTful API architectures following Richardson Maturity Model standards with robust input validation and error handling.")
    p("• Architecting a multi-tiered data storage strategy, segregating ephemeral in-memory state in Redis from durable transactional persistence in PostgreSQL.")
    p("• Investigating concurrency failure modes, race conditions, and distributed lock contention in high-velocity bidding environments.")
    p("• Implementing real-time event streaming mechanisms using STOMP WebSockets to maintain synchronized state between server and clients.")
    p("• Subjecting the application to distributed load and stress testing using Grafana k6 to uncover systemic bottlenecks under 1,000 concurrent Virtual Users.")
    p("• Formulating and implementing architectural remediations to optimize latency, throughput, and resource utilization.")
    p("The scope was strictly focused on backend architecture, domain services, persistence engineering, and concurrency optimization. Frontend interfaces were utilized exclusively for verification and automated browser testing.")

    h2("1.5 Project Overview: BidDrive Core")
    p("BidDrive Core is a specialized backend engine engineered for on-demand ride-hailing platforms. In conventional mobility platforms (such as Uber and Ola), pricing is calculated unilaterally by centralized algorithms that inflate prices during demand spikes. In contrast, BidDrive Core introduces an interactive reverse-auction mechanism inspired by inDrive, supplemented by an algorithmic instant booking option.")
    p("Under the reverse-auction paradigm, a passenger posts a ride with route coordinates and an initial proposed base fare. This action opens an ephemeral 90-second bidding window. Nearby drivers within a 5.0 km radius receive targeted notifications and are invited to submit lower counter-bids. Competing drivers bid downwards to secure the ride. When the timer expires, the lowest valid bidder is automatically awarded the ride, or the passenger may choose to accept a satisfactory bid early.")
    p("Hypothetical Example: A passenger requests a ride from Anna Nagar to Chennai Central (12.4 km) proposing an initial fare of ₹250. During the active bidding window, Driver A (Auto) submits a bid of ₹230, Driver B counter-bids at ₹210, and Driver C bids ₹195. At the conclusion of the bidding window, Driver C's bid of ₹195 is selected as the winning bid, saving the passenger ₹55 while providing Driver C with immediate ride allocation.")

    h2("1.6 Project Requirements and Engineering Challenges")
    p("Developing a real-time ride-fare auction engine introduces intricate functional and non-functional engineering requirements:")
    p("Functional Requirements:")
    p("• Ride Initialization: Ingestion of pickup/drop coordinates, vehicle category selection, and base fare calculation.")
    p("• Time-Bounded Reverse Bidding: Opening an ephemeral bidding window, enforcing price decrement rules, and rejecting bids exceeding allowed bounds.")
    p("• Dual Booking Modes: Providing both a 90-second Reverse Auction mode and an immediate Algorithmic Instant Dispatch mode.")
    p("• Automated Match Resolution: Deterministic selection of the lowest valid bidder upon auction expiry or early acceptance.")
    p("• Boarding Verification: Generation of a cryptographic 4-digit PIN (OTP) presented by the passenger and validated by the driver prior to trip start.")
    
    p("Engineering Challenges:")
    p("• The Concurrency Dilemma: A system that functions flawlessly for sequential single-user tests often degrades catastrophic failure modes when hundreds of drivers submit counter-bids simultaneously.")
    p("• Distributed Race Conditions: If two drivers bid simultaneously against the same auction, naive read-modify-write patterns can overwrite lower bids or corrupt the auction ledger.")
    p("• Resource Starvation: Relational database connections, CPU cycles for cryptographic hashing, and operating system threads become heavily contended under surge traffic.")

    h2("1.7 Project Technology Overview")
    p("To address these architectural requirements, a cohesive modern technology stack was selected:")
    
    tech_stack = [
        ["Java 21 LTS", "Core programming runtime leveraging Project Loom Virtual Threads for ultra-high concurrency."],
        ["Spring Boot 3.4.0", "Enterprise framework providing dependency injection, Spring MVC, and transaction management."],
        ["PostgreSQL 16", "ACID-compliant relational database for durable entity storage and historical auditing."],
        ["Redis 7.0", "In-memory data store for geospatial indexing (GEOADD/GEOSEARCH), 90s auction TTL, and atomic Lua scripts."],
        ["Spring Data JPA", "Object-Relational Mapping (ORM) abstraction leveraging Hibernate 6 for database interactions."],
        ["Flyway", "Version-controlled, automated database schema migration management."],
        ["Spring Security & JJWT", "Stateless authentication filter pipeline enforcing HMAC-SHA256 bearer tokens and RBAC."],
        ["STOMP / WebSockets", "Bi-directional real-time push broker over SockJS for live bid tickers and proximity alerts."],
        ["Grafana k6", "Distributed load testing engine used to benchmark 1,000 concurrent Virtual Users."],
        ["Playwright", "Multi-user browser automation framework for dual-context end-to-end integration tests."],
        ["JUnit 5 & Mockito", "Unit and slice testing suite providing comprehensive domain regression coverage."]
    ]
    add_table_data(["Technology", "Role & Justification in BidDrive Core"], tech_stack, "Table 1.1: Core Technology Stack of BidDrive Core")

    print("Building Chapter 2...")
    # ==========================================
    # CHAPTER 2 — PROJECT DESIGN AND IMPLEMENTATION
    # ==========================================
    h1("CHAPTER 2 — PROJECT DESIGN AND IMPLEMENTATION")

    h2("2.1 Project Workflow")
    p("The operational lifecycle of BidDrive Core encompasses eight sequential stages, transitioning rides through deterministic states while coordinating memory and persistent storage tiers:")
    p("Step 1 — Ride Creation: The passenger submits pickup coordinates, destination coordinates, vehicle class, and an initial base fare via `POST /api/rides`. The REST controller validates parameters and passes the request to `RideService`.")
    p("Step 2 — Geospatial Radius Search: `DriverLocationRedisService` queries the Redis Geo index (`drivers:locations`) to discover all available drivers within a 5.0 km radius using spherical coordinates.")
    p("Step 3 — Auction Initialization: `AuctionRedisService` instantiates a Redis Hash key `auction:ride:{rideId}` with a 90-second Time-To-Live (TTL), initializing the current lowest bid to the base fare.")
    p("Step 4 — Real-Time Radar Broadcast: The STOMP message broker broadcasts the ride request to `/topic/rides` and dispatches targeted private alerts to nearby drivers' queues (`/user/{email}/queue/rides`).")
    p("Step 5 — Driver Counter-Bidding: Nearby drivers submit bids via `POST /api/bids`. Each bid is processed atomically against Redis using a server-side Lua Compare-and-Swap script.")
    p("Step 6 — Live Ticker Synchronization: Winning bids are pushed to `/topic/auction/{rideId}`, updating the passenger's live glowing bid ticker and notifying competing drivers in sub-millisecond time.")
    p("Step 7 — Match Resolution: The auction resolves either via passenger manual acceptance or automated expiry at 90 seconds via Redis Keyspace Notifications (`__keyevent@0__:expired`). The winning bid is flagged as `ACCEPTED_LOWEST`, the ride status becomes `MATCHED`, and a 4-digit boarding OTP is generated.")
    p("Step 8 — Trip Execution & Settlement: The driver verifies passenger OTP, advances through `IN_PROGRESS`, and marks `COMPLETED`. The platform credits driver earnings to `DriverWallet` and records rolling driver ratings.")

    add_callout_box("Figure 2.1: BidDrive Core End-to-End Workflow Flowchart",
        "  [Passenger Submits Ride Request]\n"
        "                 │\n"
        "                 ▼\n"
        "  [Calculate Haversine Distance & Suggested Base Fare]\n"
        "                 │\n"
        "       Booking Mode Selected?\n"
        "        ├── [INSTANT DISPATCH] ──> [Redis GEOSEARCH 5km] ──> [Filter Rating/Proximity] ──> [Immediate MATCHED + OTP]\n"
        "        └── [REVERSE AUCTION]  ──>\n"
        "                 │\n"
        "  [Instantiate Redis Hash: auction:ride:{id} with 90s TTL]\n"
        "                 │\n"
        "  [Broadcast to /topic/rides & Proximity Alerts to /user/queue/rides]\n"
        "                 │\n"
        "  [Drivers Submit Bids via Atomic Redis Lua CAS Script]\n"
        "        ├── NewBid >= CurrentLowest ──> [REJECT: HTTP 400 Outbid (0.1ms, 0 DB Conns)]\n"
        "        └── NewBid < CurrentLowest  ──> [ACCEPT: Update Redis RAM + WebSocket Push Ticker]\n"
        "                 │\n"
        "       Resolution Condition?\n"
        "        ├── [Early Passenger Acceptance] ──> [Cancel Redis TTL] ──>\n"
        "        └── [90s Redis Timer Expires]    ──> [Keyspace Event]   ──>\n"
        "                 │\n"
        "  [Update Ride to MATCHED, Assign Lowest Driver, Mark Driver BUSY, Issue 4-Digit OTP]"
    )

    h2("2.2 System Architecture")
    p("BidDrive Core adopts a clean, layered architectural design engineered to isolate presentation protocols, business invariants, in-memory caches, and persistent storage:")
    p("• Ingress & Security Layer: Intercepts all incoming HTTP REST and WebSocket STOMP traffic. The `JwtAuthFilter` extracts and verifies bearer tokens, populating Spring's `SecurityContextHolder`. The `WebSocketAuthInterceptor` inspects STOMP `CONNECT` frames to enforce token authentication at the socket level.")
    p("• Controller Layer: Exposes versioned REST endpoints (`/api/rides`, `/api/bids`, `/api/drivers`, `/api/auth`). Controllers perform boundary validation via Jakarta Bean Validation (`@Valid`, `@NotNull`) and delegate to services.")
    p("• Domain Service Layer: Encapsulates pure business logic. `RideService` governs the ride state machine; `AuctionRedisService` manages in-memory bidding; `GeoDistanceService` calculates spherical distances; `FareCalculationService` computes fares and commissions.")
    p("• In-Memory Cache Tier (Redis 7): Operates as the high-throughput hot path. Manages geospatial driver indexes (`drivers:locations`), ephemeral auction keys (`auction:ride:{id}`), and executes atomic Lua scripts.")
    p("• Relational Persistence Tier (PostgreSQL 16): Maintains the durable source of truth. Handles structured tables for users, rides, bids, wallets, and ratings with Flyway migrations.")

    h2("2.3 Backend Application Design")
    p("The backend codebase is structured into cohesive packages according to domain responsibilities:")
    
    app_design_table = [
        ["com.biddrive.core.controllers", "Exposes REST endpoints; serializes and deserializes DTOs; maps HTTP status codes."],
        ["com.biddrive.core.services", "Implements business invariants, transaction boundaries, and coordinating logic."],
        ["com.biddrive.core.models", "JPA entity classes mapping relational tables with integrity constraints."],
        ["com.biddrive.core.repositories", "Spring Data JPA repository interfaces executing indexed database queries."],
        ["com.biddrive.core.dtos", "Data Transfer Objects decoupling external API contracts from internal JPA models."],
        ["com.biddrive.core.security", "Spring Security 6 configuration, JWT utility classes, and custom filters."],
        ["com.biddrive.core.websocket", "STOMP broker configuration, channel interceptors, and real-time event publishers."],
        ["com.biddrive.core.listeners", "Redis Keyspace Expiration listeners for automated auction match resolution."]
    ]
    add_table_data(["Package Namespace", "Architectural Role & Description"], app_design_table, "Table 2.1: Layered Package Organization of BidDrive Core")

    h2("2.4 REST API Development")
    p("The REST API is structured adhering strictly to RESTful conventions, returning standardized HTTP status codes and uniform JSON payloads:")
    
    api_endpoints = [
        ["POST", "/api/auth/register", "Registers a new driver or passenger with BCrypt hashed credentials; returns JWT token.", "201 Created"],
        ["POST", "/api/auth/login", "Authenticates user credentials against database; returns 24-hour JWT bearer token.", "200 OK"],
        ["POST", "/api/rides", "Passenger initiates a 90-second reverse auction ride with route coordinates and base fare.", "201 Created"],
        ["POST", "/api/rides/instant", "Passenger books an immediate ride; matches nearest compatible driver synchronously.", "201 Created"],
        ["GET", "/api/rides", "Retrieves active rides filtered by status (e.g. INITIATED) ordered by latest creation.", "200 OK"],
        ["GET", "/api/rides/{id}", "Fetches detailed ride state; includes 4-digit OTP only if requester is the passenger.", "200 OK"],
        ["GET", "/api/rides/{id}/auction", "Polls live auction details directly from Redis RAM (current lowest bid, remaining TTL).", "200 OK"],
        ["POST", "/api/bids", "Driver submits a counter-bid; processed atomically via Redis Lua Compare-and-Swap.", "201 Created"],
        ["PUT", "/api/bids/{id}/accept", "Passenger manually accepts a driver's bid early; transitions ride to MATCHED.", "200 OK"],
        ["PUT", "/api/drivers/location", "Driver sends high-frequency GPS ping; recorded in Redis Geospatial sorted set.", "200 OK"],
        ["POST", "/api/rides/{id}/start", "Driver inputs passenger's 4-digit OTP; verifies boarding and starts trip.", "200 OK"],
        ["POST", "/api/rides/{id}/complete", "Driver completes ride; triggers automatic wallet credit and reverts driver to Available.", "200 OK"]
    ]
    add_table_data(["Method", "Endpoint Path", "Functional Purpose", "HTTP Status"], api_endpoints, "Table 2.2: Core REST API Endpoints in BidDrive Core")

    h2("2.5 Redis-Based Auction State Management")
    p("A paramount design decision in BidDrive Core is the total decoupling of the high-frequency auction hot path from the relational database. If every bid placement, remaining countdown poll, and GPS update triggered relational database writes, connection pools would saturate instantly.")
    p("Instead, active auctions are represented as ephemeral Redis Hash keys:")
    p("• Key Pattern: `auction:ride:{rideId}`")
    p("• Configured Duration: Exactly 90 Seconds (`auction.ttl-seconds=90`)")
    p("• Internal Fields: `rideId`, `lowestBid`, `baseFare`, `lowestDriverId`, `status: ACTIVE`, `durationSeconds: 90`")
    p("When a client queries `GET /api/rides/{id}/auction`, the service executes `HGETALL` and `TTL` directly against Redis RAM in $< 1\text{ ms}$, returning the remaining seconds down to the millisecond without consuming any PostgreSQL JDBC connections.")

    h2("2.6 Bid Validation and Atomic Processing")
    p("In a competitive reverse auction, multiple drivers frequently submit counter-bids within milliseconds of each other. Naive multi-threaded processing creates dangerous race conditions:")
    p("Concrete Race Condition Scenario: Current lowest bid is ₹200. Driver A submits ₹180 and Driver B submits ₹170 concurrently. Under standard application-level read-modify-write: Thread A reads ₹200 and validates ₹180. Thread B reads ₹200 and validates ₹170. Thread A writes ₹180. Thread B writes ₹170. However, if Thread A's write completes slightly after Thread B due to thread scheduling, the stored lowest bid becomes ₹180—overwriting a genuinely superior bid of ₹170!")
    p("To eliminate this failure mode across both single-JVM and horizontally clustered deployments, BidDrive Core executes all bid updates via an indivisible Redis Lua Script:")
    p("1. Checks key existence (`EXISTS key`).")
    p("2. Validates bid ceiling ($\text{bid} \le \text{baseFare} + ₹100$) and positive value ($\text{bid} > 0$).")
    p("3. Executes Compare-and-Swap: evaluates whether incoming bid is strictly lower than current `lowestBid`.")
    p("4. If valid, atomically updates `lowestBid` and `lowestDriverId` in Redis memory.")
    p("5. Returns integer status code (`1` = accepted lowest, `0` = outbid, `-1` = expired, `-2` = invalid amount).")
    p("Rejected bids terminate in $< 0.1\text{ ms}$ at the cache layer with HTTP 400, protecting the database entirely.")

    h2("2.7 Auction Lifecycle Management")
    p("The auction lifecycle transitions through strict temporal states governed by Redis TTL countdowns:")
    p("• Active Window: 90 seconds from ride creation.")
    p("• Resolution Path A (Early Acceptance): The passenger observes incoming bids on their live ticker and accepts a preferred driver before the 90 seconds elapse. The server marks the bid `ACCEPTED`, marks all other bids `REJECTED`, deletes the Redis key (`closeAuction`), transitions the ride to `MATCHED`, and generates the boarding OTP.")
    p("• Resolution Path B (Automated Timer Expiry): If the passenger does not manually accept a bid, the Redis key counts down to 0 and expires. Redis emits a keyspace event `__keyevent@0__:expired`. `RedisExpirationListener` catches the event and invokes `AuctionFinishService.finalizeAuction(rideId)`.")
    p("• Auto-Match Logic: If bids were placed, the lowest bidder is selected, marked `ACCEPTED_LOWEST`, the ride transitions to `MATCHED`, and the winning driver is marked `BUSY`. If zero bids were submitted, the ride status transitions to `EXPIRED`.")

    h2("2.8 Database Design and Transactional Persistence")
    p("PostgreSQL 16 serves as the immutable, durable ledger for the system. The schema enforces strict entity relationships and constraints:")
    p("• `passengers`: Primary key `id`, unique email, unique phone number, BCrypt password hash, timestamp.")
    p("• `drivers`: Primary key `id`, license plate, vehicle class, rating (`NUMERIC(3,2)`), status (`Available`/`BUSY`), GPS coordinates.")
    p("• `rides`: Primary key `id`, foreign keys to `passenger_id` and `driver_id`, route coordinates, distance in km, `ride_type` (`AUCTION`/`INSTANT`), status, 4-digit `otp`, timestamps.")
    p("• `bids`: Primary key `id`, foreign keys to `ride_id` and `driver_id`, numeric `bid_amount`, status (`ACCEPTED_LOWEST`, `ACCEPTED`, `REJECTED`, `OUTBID`).")
    p("• `driver_wallets`: Foreign key `driver_id`, `available_balance`, `total_gross_earnings`, `total_commission_paid`, `total_trips_completed`.")
    p("• `ratings`: Composite unique constraint on `(ride_id, rater_id)`, 1-to-5 star score, optional text feedback.")

    h2("2.9 Real-Time Communication")
    p("To ensure a responsive user experience, BidDrive Core implements a real-time publish-subscribe messaging architecture over STOMP WebSockets with SockJS fallback:")
    p("• `/topic/rides`: Public broadcast channel. When a ride is created, all active drivers listening on this channel observe the new trip opportunity.")
    p("• `/topic/auction/{rideId}`: Scoped auction channel. When a driver places a valid new lowest bid, the updated fare and driver ID are published immediately, updating the passenger's glowing bid ticker.")
    p("• `/user/{email}/queue/rides`: Private targeted channel. Drivers within the 5.0 km radius receive direct radar alerts with exact distance calculations.")
    p("• `/user/{email}/queue/notifications`: Private direct notification channel for match announcements, driver arrival alerts, and boarding OTP credentials.")

    h2("2.10 No-Bid Fallback and Retry Workflow")
    p("When an auction expires without receiving any driver bids, the system prevents deadlocks through automated fallback workflows:")
    p("1. Status Transition: The ride is marked as `EXPIRED` in PostgreSQL, releasing any temporary resources.")
    p("2. Passenger Real-Time Alert: The WebSocket publisher pushes an `AUCTION_EXPIRED` event to the passenger's private queue.")
    p("3. Interactive Options: The passenger client displays two remediation options:")
    p("   a. Retry Auction (`POST /api/rides/{id}/retry`): Passenger can increase the base fare to incentivize drivers, re-opening a fresh 90-second bidding window.")
    p("   b. Convert to Instant (`POST /api/rides/{id}/convert-to-instant`): Passenger can convert the expired auction into an instant ride, allowing the platform to match the nearest available driver at the standard platform fare.")

    print("Building Chapter 3...")
    # ==========================================
    # CHAPTER 3 — TECHNOLOGY AND SYSTEM SPECIFICATIONS
    # ==========================================
    h1("CHAPTER 3 — TECHNOLOGY AND SYSTEM SPECIFICATIONS")

    h2("3.1 Java and Spring Boot")
    p("The application runtime is built on Java 21 LTS (Adoptium OpenJDK) and Spring Boot 3.4.0. Java 21 provides modern language features, including records for immutable DTO definitions, pattern matching for switch expressions, and most critically, Project Loom Virtual Threads.")
    p("Spring Boot provides inversion of control, automated configuration, and structured transaction management. Web requests are handled via Spring MVC, database persistence through Spring Data JPA with Hibernate 6, and security via Spring Security 6.")

    h2("3.2 PostgreSQL and Spring Data JPA")
    p("PostgreSQL 16 serves as the persistent relational database, configured with Flyway for version-controlled, immutable schema migrations. The application uses Spring Data JPA repositories extending `JpaRepository<T, ID>`, providing declarative CRUD methods and custom JPQL/native queries.")
    p("To prevent database deadlocks and connection leaks under load, repository queries adhere to strict indexing rules, and transactions are demarcated using `@Transactional` at the service boundary.")

    h2("3.3 Redis and Time-to-Live Management")
    p("Redis 7.0 is deployed in-memory, providing sub-millisecond read/write latencies. It fulfills three critical operational functions in BidDrive Core:")
    p("1. 2D Geospatial Indexing: Driver GPS coordinates are stored in a Redis Sorted Set under `drivers:locations` using `GEOADD`. Proximity queries execute via `GEOSEARCH` in $O(N + \log M)$ time.")
    p("2. Ephemeral Hash Countdown: Active auctions use Redis Hashes with exact 90-second TTLs.")
    p("3. Keyspace Notifications: Redis is configured with `notify-keyspace-events = Ex` to publish expiration events to `__keyevent@0__:expired`, driving automated background auction matching.")

    h2("3.4 Concurrency Control")
    p("Concurrency control in BidDrive Core is designed with a defense-in-depth approach across multiple layers:")
    p("• Application Layer: Java 21 Virtual Threads decouple incoming request concurrency from operating system kernel thread limits.")
    p("• Cache Layer: Atomic Redis Lua scripts serialize concurrent bid submissions on the single-threaded Redis event loop, preventing distributed race conditions across application instances.")
    p("• Database Layer: Relational constraints (unique indexes on phone numbers, emails, active ratings) and pessimistic/optimistic locking prevent duplicate records and double-spending.")

    h2("3.5 Transaction Management and Database Locking")
    p("Database transactions follow ACID principles. Service methods altering entity state are annotated with `@Transactional(rollbackFor = Exception.class)`. If an exception occurs during ride matching, any associated database writes (bid status updates, ride state changes, driver status updates) are rolled back atomically.")
    p("Critical Design Note: Relational database transactions cannot encompass Redis operations. Therefore, BidDrive Core coordinates state changes by ensuring that Redis mutations occur first; if PostgreSQL persistence fails, compensating logic closes or reverts the Redis key to maintain consistency.")

    h2("3.6 Real-Time Event Delivery")
    p("Real-time messaging is implemented using STOMP over SockJS. STOMP provides structured frame-based semantics (`CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`), enabling topic-based broadcasting and user-targeted queue routing. This was chosen over raw Server-Sent Events (SSE) because STOMP supports bidirectional socket communication and granular channel subscriptions out-of-the-box.")

    h2("3.7 Development and Testing Tools")
    p("The development and quality assurance workflow utilized a suite of specialized engineering tools:")
    p("• Apache Maven: Dependency resolution, build lifecycle management, and packaging.")
    p("• Git: Version control, branch-based feature development, and commit history tracking.")
    p("• Docker & Docker Compose: Multi-container orchestration for PostgreSQL 16 and Redis 7.")
    p("• Postman: Manual endpoint exploration, payload verification, and security testing.")
    p("• JUnit 5 & Mockito: Automated unit and slice testing across domain services.")
    p("• Playwright: Automated dual-browser end-to-end integration testing in Chromium.")
    p("• Grafana k6: High-concurrency distributed load testing engine.")

    print("Building Chapter 4...")
    # ==========================================
    # CHAPTER 4 — DEVELOPMENT STANDARDS, TESTING AND PERFORMANCE ANALYSIS
    # ==========================================
    h1("CHAPTER 4 — DEVELOPMENT STANDARDS, TESTING AND PERFORMANCE ANALYSIS")

    h2("4.1 Backend Development Practices")
    p("To maintain high engineering standards, the project adheres to established backend development patterns:")
    p("• Separation of Concerns: Clear division between HTTP transport (Controllers), business invariants (Services), and data access (Repositories).")
    p("• DTO Encapsulation: Internal JPA entities are never exposed directly to REST clients; dedicated Request and Response DTOs prevent accidental over-posting and field leakage.")
    p("• Defensive Programming: Comprehensive input validation via Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Min`, `@Max`).")
    p("• Centralized Exception Handling: A `@RestControllerAdvice` class (`GlobalExceptionHandler`) intercepts domain exceptions, returning RFC 7807 compliant JSON error structures.")

    h2("4.2 API Validation and Error Handling")
    p("All inbound client requests undergo structural and logical validation. Structural validation checks parameter formats, email syntax, and non-null constraints. Logical validation enforces domain rules (e.g. driver must be in `Available` status; bid amount must be strictly positive and below the fare ceiling).")
    p("When a rule is violated, the API returns a structured error response with appropriate HTTP status codes (400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict).")

    h2("4.3 Data Consistency and Reliability")
    p("Maintaining absolute consistency across driver availability, ride status, and bidding audit records is essential for platform trustworthiness. BidDrive Core enforces two core invariants:")
    p("1. Single Active Ride Invariant: A passenger cannot initiate a second ride if an active ride exists in `INITIATED`, `MATCHED`, `DRIVER_ARRIVING`, `ARRIVED`, or `IN_PROGRESS` status.")
    p("2. Driver Availability Invariant: A driver automatically flips from `Available` to `BUSY` upon matching, preventing double-dispatch across concurrent auctions. Status reverts to `Available` only upon trip completion or cancellation.")

    h2("4.4 Functional and Integration Testing")
    p("Automated testing was conducted across the testing pyramid. The unit and slice testing suite comprises **31 test cases** implemented using JUnit 5 and Mockito in `core/core/src/test/java/com/biddrive/core/`:")
    p("• `AuthServiceTest`: Validates BCrypt user registration, JWT generation, and role extraction.")
    p("• `BidServiceTest`: Validates reverse auction ceiling rules, outbid logic, and ownership verification.")
    p("• `RideLifecycleServiceTest`: 9 comprehensive test cases covering every valid and invalid transition in the state machine, invalid OTP rejection, and driver status reversion.")
    p("• `FareAndCommissionTest`: Validates Haversine distance math, per-km/per-min rates, and commission splits.")
    p("• `WalletServiceTest`: Validates ledger arithmetic, commission deductions, and balance updates.")
    p("• `RatingServiceTest`: Validates atomic rolling average rating calculation.")
    p("Result: **100% Pass Rate (31 passed, 0 failures, 0 errors).**")

    h2("4.5 Concurrency Testing")
    p("Automated end-to-end multi-user integration tests were built using Playwright (`biddrive-frontend/e2e/`):")
    p("• `multi-user-reverse-auction.spec.ts`: Spawns two isolated browser contexts simultaneously (Passenger Chrome session + Driver Chrome session). Verifies that when the passenger creates an auction, the driver receives a live WebSocket broadcast, submits a counter-bid, the passenger's glowing ticker updates in real time, and accepting the bid issues the 4-digit OTP.")
    p("• `ride-lifecycle.spec.ts`: Validates the instant booking flow, OTP entry, full state progression (`DRIVER_ARRIVING` ➔ `ARRIVED` ➔ `IN_PROGRESS` ➔ `COMPLETED`), and driver rating submission.")
    p("• `no-drivers-fallback.spec.ts`: Verifies non-blocking fallback notifications when no drivers are within 5 km.")

    h2("4.6 Load Testing Using k6")
    p("To evaluate system resilience under peak rush-hour concurrency, distributed load testing was executed using Grafana k6 (`load-tests/load_test.js`). The test simulated **1,000 Concurrent Virtual Users (VUs)** over a 3-minute execution window:")
    p("• 750 Drivers (75% Load): Register, authenticate, ping GPS coordinates to Redis every 2s, browse active rides, and submit atomic bids.")
    p("• 250 Passengers (25% Load): Register, authenticate, create 90-second reverse auctions, poll live state, and accept bids.")

    h2("4.7 Performance Results and Threshold Analysis")
    p("During the initial load testing of the unhardened baseline architecture, severe performance degradation was recorded. The table below documents the formal SLA thresholds alongside the recorded unhardened baseline results:")

    perf_baseline_table = [
        ["Auction bid duration, P95", "Below 300 ms", "8.32 s", "FAILED — Severe database lock contention on bids table."],
        ["Auction bid error rate", "Below 5%", "48.46%", "FAILED — Timeouts and database connection pool starvation."],
        ["Check assertion pass rate", "Above 99%", "88.83%", "FAILED — 11.17% of HTTP requests returned 5xx or timed out."],
        ["HTTP request duration, P95", "Below 500 ms", "9.52 s", "FAILED — Extreme queueing latency across Tomcat threads."],
        ["HTTP request duration, P99", "Below 1.5 s", "22.53 s", "FAILED — Tail latency degraded due to blocked thread stacks."],
        ["HTTP request failure rate", "Below 1%", "20.24%", "FAILED — 1 in 5 requests failed to complete successfully."]
    ]
    add_table_data(["Performance Metric", "Target Threshold", "Observed Baseline Result", "Evaluation & Architectural Diagnosis"], perf_baseline_table, "Table 4.1: Initial Load Test Performance Threshold Analysis (Baseline Architecture)")

    p("Objective Evaluation of Findings:")
    p("1. Latency Breaches: HTTP P95 latency (9.52s) exceeded the SLA target by over 19x. Auction bid placement P95 (8.32s) exceeded the target by over 27x.")
    p("2. High Failure Rate: The request failure rate of 20.24% demonstrated that under heavy concurrent load, the un-tuned system could not provide acceptable availability.")
    p("3. Systemic Starvation: The test exposed acute bottlenecks in thread allocation, database connection pooling, and unindexed queries.")

    h2("4.8 Database and Application Bottlenecks")
    p("A rigorous performance audit identified four primary internal bottlenecks:")
    p("A. Row-Level Lock Contention: Multiple threads attempting to update the same auction ride record in PostgreSQL blocked on row-level locks, causing transaction queues to back up.")
    p("B. Connection-Pool Exhaustion: Default HikariCP pool size (10 connections) caused hundreds of concurrent virtual threads to time out waiting for an available database connection.")
    p("C. Open Session In View (OSIV) Anti-Pattern: `spring.jpa.open-in-view=true` kept database connections open during JSON serialization, needlessly inflating connection hold times by 50–100ms per request.")
    p("D. Sequential Table Scans: In the absence of composite indexes, high-frequency queries like `GET /api/rides?status=INITIATED` forced full sequential scans across thousands of rows.")

    h2("4.9 Network and Resource Constraints")
    p("External and runtime constraints compounded the application bottlenecks:")
    p("• Tomcat Platform Thread Saturation: 200 platform threads blocked on I/O, exhausting kernel thread stack memory.")
    p("• Redis Single-Socket Contention: A single multiplexed Lettuce socket queued hundreds of concurrent commands, causing artificial latency spikes.")
    p("• Console I/O Contention: `spring.jpa.show-sql=true` caused severe monitor lock contention on `java.io.PrintStream`, degrading application throughput by up to 50%.")
    p("• BCrypt Work Factor Saturation: Cost factor 10 required 100ms of CPU time per password hash, saturating host CPU at 100% during user ramp-up waves.")

    h2("4.10 Optimization Opportunities and Future Improvements")
    p("Through systematic performance engineering sprints, all 7 identified bottlenecks were resolved through targeted architectural remediations:")
    
    bottleneck_fixes = [
        ["1. Tomcat Thread Starvation", "Enabled Java 21 Project Loom Virtual Threads (`spring.threads.virtual.enabled=true`). Lightweight virtual threads unmount during blocking I/O, eliminating thread exhaustion.", "Average latency dropped from 680ms to 120ms; stack memory dropped by 90%."],
        ["2. Redis Single-Socket Bottleneck", "Configured Lettuce connection pooling via `commons-pool2` (`max-active=200`, `max-idle=50`). Parallelized Redis socket traffic.", "Driver GPS ping P95 latency dropped from 920ms to 3.0ms (99.6% reduction)."],
        ["3. Clustered Race Conditions", "Replaced single-JVM `synchronized` blocks with an atomic server-side Redis Lua Compare-and-Swap script (`SUBMIT_BID_SCRIPT`).", "Bid placement P95 latency dropped from 1,240ms to 42ms; 0 corrupted bids."],
        ["4. HikariCP Pool Starvation", "Disabled Open-Session-In-View (`open-in-view=false`) and expanded HikariCP pool to 30 connections with optimized timeouts.", "Connection hold time reduced by 85%; zero connection timeout exceptions."],
        ["5. PostgreSQL Table Scans", "Installed composite B-Tree indexes via Flyway `V2__performance_indexes.sql` (`idx_rides_status_created_at`, `idx_bids_ride_status`).", "Query execution dropped from 18.6ms to 0.85ms; DB CPU dropped from 95% to 18%."],
        ["6. Console I/O Lock Contention", "Disabled verbose SQL logging in production profiles (`show-sql=false`, `format_sql=false`).", "Eliminated PrintStream monitor locks; reclaimed 30%–50% application throughput."],
        ["7. BCrypt CPU Saturation", "Configured tunable BCrypt cost factor (`bcrypt.strength=4` for stress testing profile).", "Registration CPU dropped from 100% to < 8%, isolating true system concurrency."]
    ]
    add_table_data(["Identified Bottleneck", "Architectural Remediation Implemented", "Measured Verification & Impact"], bottleneck_fixes, "Table 4.2: Summary of Bottlenecks Identified and Architectural Remediations")

    p("Post-Remediation Verification Results:")
    p("Following these remediations, the identical 1,000 VU stress test was re-executed. P95 latency dropped from 2,450ms to **380 ms** (🔻 **84.5% reduction**); error rate dropped from 8.41% to **0.08%** (🔻 **99.0% reduction**); check pass rate reached **99.3%**; and throughput scaled to **48,230+ requests** at **267.9 req/sec** (**3.4x throughput multiplier**), successfully meeting all production SLA criteria.")

    print("Building Chapter 5...")
    # ==========================================
    # CHAPTER 5 — CONCLUSION
    # ==========================================
    h1("CHAPTER 5 — CONCLUSION")

    h2("5.1 Conclusion")
    p("The industrial summer internship at Twozo Technologies provided an invaluable, rigorous opportunity to design, implement, and benchmark BidDrive Core—a real-time, high-throughput mobility backend engine. By bridging the economic transparency of reverse auctions with the dispatch velocity of instant booking, the project addressed real-world challenges in decentralized urban transportation.")
    p("Through the disciplined application of Java 21 LTS, Spring Boot 3.4, Redis 7 in-memory geospatial and Lua data structures, PostgreSQL 16 transactional persistence, and STOMP WebSockets, the project realized a resilient distributed architecture. The rigorous quality assurance regimen—spanning 31 unit tests, multi-browser Playwright E2E suites, and distributed k6 load testing under 1,000 Virtual Users—demonstrated that building high-concurrency systems requires a profound understanding of operating system thread scheduling, connection pooling, cache atomicity, and database indexing physics.")
    p("The systematic discovery and elimination of the 7 performance bottlenecks reinforced the vital engineering lesson that theoretical designs must always be validated against empirical telemetry. BidDrive Core stands as a robust, production-hardened foundation for next-generation on-demand mobility platforms.")

    h2("5.2 Technical Learning Outcomes")
    p("The internship yielded significant professional and technical learning outcomes across multiple software engineering domains:")
    p("1. Advanced Java & Spring Boot Architecture: Mastered enterprise layered architecture, dependency injection, Bean Validation, and Project Loom Virtual Threads.")
    p("2. Distributed In-Memory Engineering: Acquired practical expertise in Redis data modeling, geospatial indexing, TTL keyspace events, and atomic Lua Compare-and-Swap scripting.")
    p("3. Relational Persistence & Indexing: Gained deep insight into PostgreSQL transaction boundaries, connection pool sizing formulas, Flyway migrations, and composite B-Tree query optimization.")
    p("4. Real-Time Web Protocols: Implemented STOMP messaging over WebSockets with SockJS fallback and JWT handshake channel interceptors.")
    p("5. Concurrency & Performance Engineering: Developed hands-on proficiency in load testing methodologies with Grafana k6, identifying thread starvation, Netty channel bottlenecks, and resource lock contention.")
    p("6. Production Quality Assurance: Established automated testing discipline across unit, slice, and end-to-end multi-user browser testing.")

    h2("5.3 Future Scope")
    p("While BidDrive Core has achieved high concurrency stability, several avenues for future enhancement have been identified for post-internship development:")
    p("• Fiat Payment Gateway Integration: Integrating external payment webhooks (such as Stripe or Razorpay) for automated passenger digital wallets and instant driver bank payouts.")
    p("• Cloud-Native Kubernetes Orchestration: Packaging the services into Docker containers deployed across AWS ECS Fargate or Kubernetes (EKS) with automated horizontal pod autoscaling (HPA).")
    p("• Dynamic Route Polyline Snapping: Incorporating OpenStreetMap (OSRM) or Google Directions API to provide real-time turn-by-turn route polylines and live driver navigation updates.")
    p("• Multi-Region Cache Clustering: Deploying Redis Sentinel or Redis Cluster across geographic regions to minimize latency for distributed multi-city operations.")

    print("Building References and Appendices...")
    # ==========================================
    # REFERENCES
    # ==========================================
    h1("REFERENCES")
    
    references = [
        "1. Oracle Corporation, \"Java Platform, Standard Edition Documentation (Java 21 LTS),\" Oracle Technology Network, 2023. [Online]. Available: https://docs.oracle.com/en/java/javase/21/",
        "2. Pivotal Software / VMware, \"Spring Boot Reference Documentation (v3.4.0),\" Spring Framework Documentation, 2024. [Online]. Available: https://docs.spring.io/spring-boot/docs/current/reference/html/",
        "3. Redis Ltd., \"Redis Documentation: Geospatial, Hashes, and Lua Scripting,\" Redis Open Source Documentation, 2024. [Online]. Available: https://redis.io/docs/",
        "4. The PostgreSQL Global Development Group, \"PostgreSQL 16.0 Documentation: Performance, Indexing, and Transactions,\" PostgreSQL Documentation, 2023. [Online]. Available: https://www.postgresql.org/docs/16/",
        "5. Red Hat / Hibernate Community, \"Hibernate ORM 6.4 User Guide,\" Hibernate Documentation, 2024. [Online]. Available: https://hibernate.org/orm/documentation/",
        "6. Grafana Labs, \"k6 Documentation: High-Concurrency Distributed Load Testing,\" Grafana k6 Docs, 2024. [Online]. Available: https://k6.io/docs/",
        "7. Microsoft Corporation, \"Playwright: Fast and reliable end-to-end testing for modern web apps,\" Playwright Docs, 2024. [Online]. Available: https://playwright.dev/",
        "8. Redgate Software, \"Flyway Database Migrations Documentation,\" Flyway Community Documentation, 2024. [Online]. Available: https://flywaydb.org/documentation/",
        "9. E. Gamma, R. Helm, R. Johnson, and J. Vlissides, \"Design Patterns: Elements of Reusable Object-Oriented Software,\" Addison-Wesley Professional, 1994.",
        "10. M. Fowler, \"Patterns of Enterprise Application Architecture,\" Addison-Wesley Professional, 2002."
    ]
    for ref in references:
        p(ref, space_after=8)

    # ==========================================
    # APPENDICES
    # ==========================================
    h1("APPENDIX A — API REFERENCE")
    
    p("This appendix provides the formal request and response schemas for primary endpoints in BidDrive Core.")
    
    p("Endpoint 1: Create Auction Ride", bold=True)
    p("POST /api/rides\nHeaders: Authorization: Bearer <jwt_token>, Content-Type: application/json\nRequest Payload:\n{\n  \"pickupLocation\": \"Anna Nagar, Chennai\",\n  \"destinationLocation\": \"Chennai Central, Chennai\",\n  \"pickupLatitude\": 13.0850,\n  \"pickupLongitude\": 80.2101,\n  \"destinationLatitude\": 13.0827,\n  \"destinationLongitude\": 80.2707,\n  \"vehicleType\": \"Cab Economy\",\n  \"baseFare\": 250.00\n}\nResponse (201 Created):\n{\n  \"id\": 104,\n  \"passengerId\": 12,\n  \"rideType\": \"AUCTION\",\n  \"status\": \"INITIATED\",\n  \"baseFare\": 250.00,\n  \"estimatedDistanceKm\": 7.42,\n  \"otp\": \"5821\",\n  \"createdAt\": \"2026-05-18T10:15:30\"\n}")

    p("Endpoint 2: Submit Atomic Lua Bid", bold=True, space_before=12)
    p("POST /api/bids\nHeaders: Authorization: Bearer <jwt_token>, Content-Type: application/json\nRequest Payload:\n{\n  \"rideId\": 104,\n  \"bidAmount\": 220.00\n}\nResponse (201 Created):\n{\n  \"id\": 341,\n  \"rideId\": 104,\n  \"driverId\": 45,\n  \"bidAmount\": 220.00,\n  \"status\": \"ACCEPTED_LOWEST\",\n  \"createdAt\": \"2026-05-18T10:15:42\"\n}")

    h1("APPENDIX B — PERFORMANCE TEST SUMMARY")
    p("Summary metrics extracted from Grafana k6 distributed load testing comparing the baseline unhardened architecture against the final production-hardened release under 1,000 concurrent Virtual Users:")
    
    app_b_table = [
        ["Total Virtual Users (VUs)", "1,000 VUs (750 Drivers + 250 Passengers)", "1,000 VUs (750 Drivers + 250 Passengers)"],
        ["Execution Duration", "180 seconds (3 minutes)", "180 seconds (3 minutes)"],
        ["Total Requests Completed", "14,200 requests", "48,230 requests (3.4x throughput)"],
        ["Sustained Request Rate", "67.6 req/sec", "267.9 req/sec"],
        ["Average Response Latency", "680 ms", "120 ms (82.3% reduction)"],
        ["P95 HTTP Duration", "2,450 ms", "380 ms (84.5% reduction)"],
        ["P99 HTTP Duration", "4,890 ms", "890 ms (81.8% reduction)"],
        ["Bid Lua Execution P95", "1,240 ms (DB locking)", "42 ms (Redis Lua atomic script)"],
        ["GPS Ping P95 Latency", "920 ms (SQL writes)", "3.0 ms (Redis GEOADD)"],
        ["HTTP Error Rate", "8.41% (Timeouts & drops)", "0.08% (99.0% drop)"],
        ["Check Pass Rate", "91.6%", "99.3% (All SLA criteria met)"]
    ]
    add_table_data(["Metric Dimension", "Baseline Architecture", "Hardened Production Architecture"], app_b_table, "Table B.1: Comprehensive Comparative Performance Test Benchmark Summary")

    h1("APPENDIX C — ADDITIONAL ARCHITECTURE DIAGRAMS")
    p("This appendix documents detailed component interactions and state machines within BidDrive Core.")
    
    add_callout_box("Figure C.1: Complete Ride Finite State Machine (FSM)",
        "  [INITIATED]\n"
        "      │\n"
        "      ├── (Instant Match OR 90s Expiry / Acceptance) ──> [MATCHED]\n"
        "      ├── (90s Expiry with Zero Bids)               ──> [EXPIRED] ──> Retry OR Convert to Instant\n"
        "      └── (Passenger Early Cancellation)           ──> [CANCELLED]\n"
        "                                                             │\n"
        "  [MATCHED]\n"
        "      │\n"
        "      ├── (Driver En Route) ──> [DRIVER_ARRIVING]\n"
        "      └── (Cancellation)    ──> [CANCELLED] (Reverts Driver to Available)\n"
        "                                       │\n"
        "  [DRIVER_ARRIVING]\n"
        "      │\n"
        "      └── (Driver Reaches Pickup) ──> [ARRIVED]\n"
        "                                          │\n"
        "  [ARRIVED]\n"
        "      │\n"
        "      └── (Driver Verifies 4-Digit Passenger OTP) ──> [IN_PROGRESS]\n"
        "                                                           │\n"
        "  [IN_PROGRESS]\n"
        "      │\n"
        "      └── (Driver Reaches Destination & Ends Ride) ──> [COMPLETED]\n"
        "                                                           │\n"
        "  [COMPLETED] ──> Automatic Wallet Credit & Rolling Rating Update"
    )

    output_path = os.path.abspath("BidDrive_Core_Internship_Report.docx")
    doc.save(output_path)
    print(f"Successfully generated formal report: {output_path}")

if __name__ == "__main__":
    create_report()
