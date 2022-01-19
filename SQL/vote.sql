create table choose_question_detail
(
	content_id bigint auto_increment
		primary key,
	choose_id bigint null comment '关联到的选择题的主键',
	choose_tag varchar(32) default '' null comment '该选择题对应的前缀，如 A. B. 等',
	choose_content varchar(100) default '' null comment '该选择题选项的具体内容'
)
comment '题目为选择题的具体内容';

create table data_scope
(
	scope_id int not null
		primary key,
	scope_name varchar(32) not null
)
comment '角色可以访问的数据范围信息表';

create table role_scope
(
	role_id int not null,
	scope_id int not null
)
comment '角色信息和数据范围的关联表';

create table score_card
(
	card_id bigint auto_increment
		primary key,
	card_name varchar(100) not null,
	total_score double not null,
	degree int null
)
comment '评分卡实体对象对应的表';

create table tb_card_score
(
	card_id bigint not null comment '关联到的评分卡 ID',
	score_id bigint not null comment '本条记录关联到的评分活动',
	card_type int default 0 not null comment '本条记录所属的评分卡类型，0 表示自评分卡；
1 表示交叉评分卡，2 表示指定评分卡，3 表示负责人评分卡'
)
comment '评分活动和评分卡之间的关联关系';

create table tb_choose_question
(
	choose_id bigint auto_increment
		primary key,
	question_id bigint null comment '该字段关联到 tb_question 的 question_id，表示该题目内容记录归属于该问题',
	choose_type smallint default 0 null comment '该选择题对应的类型，0 表示单选，1 表示多选',
	choose_describe text null comment '关于该题目的具体描述',
	max_count int default 1 null comment '该选择题的最多可选的选项数，默认为 1，即单选'
);

create table tb_participate
(
	user_account varchar(32) not null comment '参与评分的用户账号',
	score_id bigint not null comment '用户参与的评分活动',
	has_accepted int default 0 not null comment '当前记录的用户是否已经接受了该邀请的标记位，默认为 0 表示未被接受'
)
comment '评分参与人员记录表';

create table tb_question
(
	question_id bigint auto_increment
		primary key,
	question_type smallint not null comment '0 表示选择题
1 表示主观题',
	question_score double default 0 not null comment '该题持有的分数'
)
comment '评分卡上的问题信息';

create table tb_role
(
	role_id int not null
		primary key,
	role_name varchar(32) not null,
	data_scope int default 0 not null
)
comment '角色信息表';

create table tb_score
(
	score_id bigint auto_increment comment '评分活动所属主键'
		primary key,
	cross_num int default 0 null comment '交叉评分份数',
	cross_flag smallint default 0 null comment '是否启用评分卡的标记列，0 表示不使用交叉评分卡，1 表示启用；
默认不启用',
	special_flag smallint default 0 null comment '是否启用指定评分卡，默认为 0 表示不启用',
	principal_flag smallint default 0 not null comment '是否启用负责人评分卡，默认为 0 表示不启用',
	start_time datetime default CURRENT_TIMESTAMP not null,
	end_time datetime null
);

create table tb_subjective_question
(
	subject_id bigint auto_increment
		primary key,
	question_id bigint null comment '该字段关联到 tb_question 的 question_id，表示该题目内容记录归属于该问题',
	subjective_describe text null comment '对于该问题的具体描述',
	score double null comment '该问题所具有的分数'
)
comment '问题列表中主观题的具体内容';

create table tb_user
(
	user_id bigint auto_increment
		primary key,
	user_account varchar(32) null,
	user_name varchar(50) null,
	user_password varchar(128) null,
	user_role int default 0 null,
	constraint tb_user_user_account_uindex
		unique (user_account)
)
comment '用户信息表';

create table user_choose
(
	gen_id bigint auto_increment comment '没有什么含义的唯一主键'
		primary key,
	user_account varchar(32) not null comment '对该选择题做出选择的用户账号',
	content_id bigint not null comment '关联到的选择题的 id，设计时保证每个评分卡的每个问题以及选项在整个系统及其生命周期都是唯一的',
	mark_time datetime default CURRENT_TIMESTAMP null comment '具体操作时间'
)
comment '用户对于选择题的选项的选择情况的记录信息';

create table user_subject
(
	gen_id bigint auto_increment comment '不具备任何含义的唯一主键'
		primary key,
	user_account varchar(32) not null comment '对该问题做出回答的用户账号',
	subjective_id bigint not null comment '回答的主观题的题目编号，在整个系统中，认为是唯一的',
	answer_content longtext null comment '具体的回答内容'
)
comment '用户对于主观题的回答记录信息表';

